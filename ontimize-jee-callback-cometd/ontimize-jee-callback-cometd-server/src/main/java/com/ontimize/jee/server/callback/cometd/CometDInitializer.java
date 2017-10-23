
package com.ontimize.jee.server.callback.cometd;

import javax.servlet.ServletContext;

import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.BayeuxServer.Extension;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerMessage.Mutable;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.DefaultSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ServletContextAware;

@Component
public class CometDInitializer implements ServletContextAware {

	private static final Logger	logger	= LoggerFactory.getLogger(CometDInitializer.class);

	private ServletContext		servletContext;

	@Bean(initMethod = "start", destroyMethod = "stop")
	public BayeuxServer bayeuxServer() {
		CometDInitializer.logger.info("Creationg Calback BayexServer");
		BayeuxServerImpl bean = new OntimizeBayeuxServerImpl();
		this.servletContext.setAttribute(BayeuxServer.ATTRIBUTE, bean);
		bean.setOption("timeout", "30000");
		bean.setOption("interval", "0");
		bean.setOption("maxInterval", "10000");
		bean.setOption("maxLazyTimeout", "5000");
		bean.setOption("long-polling.multiSessionInterval", "2000");
		bean.setOption("services", "org.cometd.examples.ChatService");
		bean.setOption("ws.cometdURLMapping", "/cometd/*");
		bean.setOption(ServletContext.class.getName(), this.servletContext);

		// bean.setTransports(new WebSocketTransport(bean), new JSONTransport(bean), new JSONPTransport(bean));
		bean.addListener(new ChannelDebugListener());
		bean.addListener(new SessionDebugListener());
		bean.addExtension(new ExtensionDebug());
		bean.setSecurityPolicy(new MyAppAuthenticator());
		return bean;
	}

	public static class MyAppAuthenticator extends DefaultSecurityPolicy implements ServerSession.RemoveListener {

		@Override
		public boolean canHandshake(BayeuxServer server, ServerSession session, ServerMessage message) {
			if (session.isLocalSession()) {
				return true;
			}

			if ((SecurityContextHolder.getContext().getAuthentication() != null) && (SecurityContextHolder.getContext().getAuthentication().getPrincipal() != null)) {
				session.addListener(this);
				return true;
			}
			return false;

			// Map<String, Object> ext = message.getExt();
			// if (ext == null) {
			// return false;
			// }

			// Map<String, Object> authentication = (Map<String, Object>) ext.get("com.myapp.authn");s
			// if (authentication == null) {
			// return false;
			// }

			// Object authenticationData = verify(authentication);
			// if (authenticationData == null) {
			// return false;
			// }

			// Authentication successful

			// Link authentication data to the session

			// Be notified when the session disappears
			// session.addListener(this);

			// return true;
		}

		@Override
		public void removed(ServerSession session, boolean expired) {
			// Unlink authentication data from the remote client
		}
	}

	@Override
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	public static class ChannelDebugListener implements BayeuxServer.ChannelListener {

		private static final Logger logger = LoggerFactory.getLogger(CometDInitializer.ChannelDebugListener.class);

		@Override
		public void configureChannel(ConfigurableServerChannel channel) {
			ChannelDebugListener.logger.debug("ConfigureChannel: {}", channel);
		}

		@Override
		public void channelAdded(ServerChannel channel) {
			ChannelDebugListener.logger.debug("ChannelAdded: {}", channel);

		}

		@Override
		public void channelRemoved(String channelId) {
			ChannelDebugListener.logger.debug("ChannelRemoved: {}", channelId);
		}

	}

	public static class SessionDebugListener implements BayeuxServer.SessionListener {

		private static final Logger logger = LoggerFactory.getLogger(CometDInitializer.SessionDebugListener.class);

		@Override
		public void sessionAdded(ServerSession session, ServerMessage message) {
			SessionDebugListener.logger.debug("SessionAdded: {}, message: {}", session, message);
		}

		@Override
		public void sessionRemoved(ServerSession session, boolean timedout) {
			SessionDebugListener.logger.debug("SessionRemoved: {}, timeout: {}", session, timedout);
		}

	}

	public static class ExtensionDebug implements Extension {

		private static final Logger logger = LoggerFactory.getLogger(CometDInitializer.ExtensionDebug.class);

		@Override
		public boolean rcv(ServerSession from, Mutable message) {
			ExtensionDebug.logger.debug("rcv from: {}, message: {}", from, message);
			return true;
		}

		@Override
		public boolean rcvMeta(ServerSession from, Mutable message) {
			ExtensionDebug.logger.debug("rcvMeta from: {}, message: {}", from, message);
			return true;
		}

		@Override
		public boolean send(ServerSession from, ServerSession to, Mutable message) {
			ExtensionDebug.logger.debug("send from: {}, to: {}, message: {}", from, to, message);
			return true;
		}

		@Override
		public boolean sendMeta(ServerSession to, Mutable message) {
			ExtensionDebug.logger.debug("sendMeta to: {}, message: {}", to, message);
			return true;
		}

	}
}
