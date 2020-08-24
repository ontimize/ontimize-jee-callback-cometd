package com.ontimize.jee.server.callback.cometd;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.transport.AsyncJSONTransport;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class OntimizeAsyncJSONTransport extends AsyncJSONTransport {

    private static final int BUFFER_CAPACITY = 512;

    public OntimizeAsyncJSONTransport(BayeuxServerImpl bayeux) {
        super(bayeux);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String encoding = request.getCharacterEncoding();
        if (encoding == null) {
            encoding = "UTF-8";
        }
        SecurityContextHolder.getContext();
        request.setCharacterEncoding(encoding);
        AsyncContext asyncContext = request.startAsync(request, response);
        // Explicitly disable the timeout, to prevent
        // that the timeout fires in case of slow reads.
        asyncContext.setTimeout(0);
        Charset charset = Charset.forName(encoding);
        ReadListener reader = "UTF-8".equals(charset.name()) ? new OntimizeUTF8Reader(request, response, asyncContext)
                : new OntimizeCharsetReader(request, response, asyncContext,
                        charset);
        ServletInputStream input = request.getInputStream();
        input.setReadListener(reader);
    }

    protected abstract class OntimizeAbstractReader extends AbstractReader {

        private final SecurityContext delegateSecurityContext;

        /**
         * The {@link SecurityContext} that was on the {@link SecurityContextHolder} prior to being set to
         * the delegateSecurityContext.
         */
        private SecurityContext originalSecurityContext;

        protected OntimizeAbstractReader(HttpServletRequest request, HttpServletResponse response,
                AsyncContext asyncContext) {
            super(request, response, asyncContext);
            this.delegateSecurityContext = SecurityContextHolder.getContext();
        }

        @Override
        protected void process(String json) throws IOException {
            this.originalSecurityContext = SecurityContextHolder.getContext();

            try {
                SecurityContextHolder.setContext(this.delegateSecurityContext);
                super.process(json);
            } finally {
                SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
                if (emptyContext.equals(this.originalSecurityContext)) {
                    SecurityContextHolder.clearContext();
                } else {
                    SecurityContextHolder.setContext(this.originalSecurityContext);
                }
                this.originalSecurityContext = null;
            }

        }

    }

    protected class OntimizeUTF8Reader extends OntimizeAbstractReader {

        private final Utf8StringBuilder content = new Utf8StringBuilder(OntimizeAsyncJSONTransport.BUFFER_CAPACITY);

        protected OntimizeUTF8Reader(HttpServletRequest request, HttpServletResponse response,
                AsyncContext asyncContext) {
            super(request, response, asyncContext);
        }

        @Override
        protected void append(byte[] buffer, int offset, int length) {
            this.content.append(buffer, offset, length);
        }

        @Override
        protected String finish() {
            return this.content.toString();
        }

    }

    protected class OntimizeCharsetReader extends OntimizeAbstractReader {

        private byte[] content = new byte[OntimizeAsyncJSONTransport.BUFFER_CAPACITY];

        private final Charset charset;

        private int count;

        public OntimizeCharsetReader(HttpServletRequest request, HttpServletResponse response,
                AsyncContext asyncContext, Charset charset) {
            super(request, response, asyncContext);
            this.charset = charset;
        }

        @Override
        protected void append(byte[] buffer, int offset, int length) {
            int size = this.content.length;
            int newSize = size;
            while ((newSize - this.count) < length) {
                newSize <<= 1;
            }

            if (newSize < 0) {
                throw new IllegalArgumentException("Message too large");
            }

            if (newSize != size) {
                byte[] newContent = new byte[newSize];
                System.arraycopy(this.content, 0, newContent, 0, this.count);
                this.content = newContent;
            }

            System.arraycopy(buffer, offset, this.content, this.count, length);
            this.count += length;
        }

        @Override
        protected String finish() {
            return new String(this.content, 0, this.count, this.charset);
        }

    }

}
