package com.ontimize.jee.desktopclient.callback.cometd;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.ConnectionEvent;

import org.apache.http.cookie.Cookie;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.ontimize.jee.common.callback.CallbackWrapperMessage;
import com.ontimize.jee.common.callback.cometd.CometDCallbackConstants;
import com.ontimize.jee.common.exceptions.OntimizeJEEException;
import com.ontimize.jee.common.exceptions.OntimizeJEERuntimeException;
import com.ontimize.jee.common.hessian.OntimizeHessianHttpClientSessionProcessorFactory;
import com.ontimize.jee.common.hessian.OntimizeHessianProxyFactoryBean;
import com.ontimize.jee.common.jackson.OntimizeMapper;
import com.ontimize.jee.common.tools.ObjectTools;
import com.ontimize.jee.common.tools.StringTools;
import com.ontimize.jee.desktopclient.callback.ICallbackClientHandler;
import com.ontimize.jee.desktopclient.callback.ICallbackEventListener;

/**
 * The Class CometDCallbackClientHandler.
 */
public class CometDCallbackClientHandler implements ICallbackClientHandler, InitializingBean {

	/** The Constant logger. */
	private static final Logger					logger					= LoggerFactory.getLogger(CometDCallbackClientHandler.class);

	/** The Constant META_CONNECT_TIMEOUT. */
	public static final long					META_CONNECT_TIMEOUT	= 20000;

	/** The Constant MAX_NETWORK_DELAY. */
	public static final long					MAX_NETWORK_DELAY		= 5000;

	/** The bayeux client. */
	private volatile BayeuxClient				bayeuxClient;

	/** The chat listener. */
	protected final MessageListener				ojeeMessageListener		= new MessageListener();

	/** The callback relative url. */
	private String								callbackRelativeUrl;

	/** The callback url. */
	private String								callbackUrl;

	/** The listeners. */
	private final List<ICallbackEventListener>	listeners;

	/**
	 * Instantiates a new comet D callback client handler.
	 */
	public CometDCallbackClientHandler() {
		super();
		this.listeners = new ArrayList<>();
	}

	/**
	 * Sets the callback relative url.
	 *
	 * @param url
	 *            the new callback relative url
	 */
	public void setCallbackRelativeUrl(String url) {
		this.callbackRelativeUrl = url;
	}

	/**
	 * Gets the callback relative url.
	 *
	 * @return the callback relative url
	 */
	public String getCallbackRelativeUrl() {
		return this.callbackRelativeUrl;
	}

	/**
	 * Gets the callback url.
	 *
	 * @return the callback url
	 */
	public String getCallbackUrl() {
		return this.callbackUrl;
	}

	/**
	 * Sets the callback url.
	 *
	 * @param callbackUrl
	 *            the new callback url
	 */
	public void setCallbackUrl(String callbackUrl) {
		this.callbackUrl = callbackUrl;
	}

	/**
	 * Gets the bayeux client.
	 *
	 * @return the bayeux client
	 */
	public BayeuxClient getBayeuxClient() {
		return this.bayeuxClient;
	}

	/**
	 * Sets the bayeux client.
	 *
	 * @param bayeuxClient
	 *            the new bayeux client
	 */
	public void setBayeuxClient(BayeuxClient bayeuxClient) {
		this.bayeuxClient = bayeuxClient;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() {
		if (!StringTools.isEmpty(this.getCallbackRelativeUrl())) {
			String base = System.getProperty(OntimizeHessianProxyFactoryBean.SERVICES_BASE_URL);
			if (base != null) {
				if ((base.charAt(base.length() - 1) != '/') && (this.getCallbackRelativeUrl().charAt(0) != '/')) {
					base = base + '/';
				}
				this.setCallbackUrl(base + this.getCallbackRelativeUrl());
			}
		}
		try {
			CometDCallbackClientHandler.this.connect();
		} catch (Exception error) {
			CometDCallbackClientHandler.logger.error("Error connecting callback cometd. WONT BE RETRIED", error);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.desktopclient.callback.ICallbackClientHandler#addWebSocketEventListener(com.ontimize.jee.desktopclient.callback.ICallbackEventListener)
	 */
	@Override
	public void addCallbackEventListener(ICallbackEventListener listener) {
		this.listeners.add(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.desktopclient.callback.ICallbackClientHandler#removeWebSocketEventListener(com.ontimize.jee.desktopclient.callback.ICallbackEventListener)
	 */
	@Override
	public void removeCallbackEventListener(ICallbackEventListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Connect.
	 *
	 * @throws Exception
	 *             the exception
	 */
	protected void connect() throws Exception {
		HttpClient httpClient = new HttpClient(new SslContextFactory(true));
		httpClient.start();
		LongPollingTransport longPollingTransport = new LongPollingTransport(null, httpClient) {
			@Override
			protected void customize(Request request) {
				super.customize(request);
				if (!StringTools.isEmpty(OntimizeHessianHttpClientSessionProcessorFactory.JWT_TOKEN)) {
					request.header("Authorization", "Bearer " + OntimizeHessianHttpClientSessionProcessorFactory.JWT_TOKEN);
				}
			}
		};
		this.setBayeuxClient(new BayeuxClient(this.getCallbackUrl(), longPollingTransport));
		// Override BayeuxClient default InLineCookieStore -> mix jetty and hessian cookies
		longPollingTransport.setCookieStore(new OJettyCookieStore());

		this.getBayeuxClient().getChannel(Channel.META_CONNECT).addListener(new ConnectionListener());

		this.getBayeuxClient().handshake();
	}

	/**
	 * Connection established.
	 */
	protected void connectionEstablished() {
		CometDCallbackClientHandler.logger.info("system: Connection to Server Opened");
		Map<String, Object> data = new HashMap<>();
		final ClientSessionChannel ojeeCallbackChannel = this.bayeuxClient.getChannel(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL);
		this.getBayeuxClient().batch(() -> ojeeCallbackChannel.subscribe(CometDCallbackClientHandler.this.ojeeMessageListener, new MessageListener() {
			@Override
			public void onMessage(ClientSessionChannel channel, Message message) {
				CometDCallbackClientHandler.logger.info("On subscribe message from connectionEstablished: {}", message);
			}
		}));

		data.put(CometDCallbackConstants.KEY_ACTION, CometDCallbackConstants.ACTION_REGISTER);
		ojeeCallbackChannel.publish(data);
	}

	/**
	 * Connection closed.
	 */
	protected void connectionClosed() {
		CometDCallbackClientHandler.logger.error("system: Connection to Server Closed");
	}

	/**
	 * Connection broken.
	 */
	protected void connectionBroken() {
		CometDCallbackClientHandler.logger.error("system: Connection to Server Broken");
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.desktopclient.callback.ICallbackClientHandler#sendMessage(java.lang.Integer, java.lang.Object)
	 */
	@Override
	public void sendMessage(Integer messageType, Object ob) throws OntimizeJEEException {
		this.sendMessage(messageType, null, ob);
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.desktopclient.callback.ICallbackClientHandler#sendMessage(java.lang.Integer, java.lang.String, java.lang.Object)
	 */
	@Override
	public void sendMessage(Integer messageType, String messageSubtype, Object ob) throws OntimizeJEEException {
		Map<String, Object> data = new HashMap<>();
		data.put(CometDCallbackConstants.KEY_ACTION, CometDCallbackConstants.ACTION_MESSAGE);
		data.put(CometDCallbackConstants.KEY_DATA, new CallbackWrapperMessage(messageType, messageSubtype, ob).serialize());
		this.bayeuxClient.getChannel(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL).publish(data);
	}

	/**
	 * Fire message event.
	 *
	 * @param wrappedMessage
	 *            the wrapped message
	 */
	protected void fireMessageEvent(CallbackWrapperMessage wrappedMessage) {
		for (ICallbackEventListener listener : this.listeners.toArray(new ICallbackEventListener[0])) {
			listener.onCallbackMessageReceived(wrappedMessage);
		}
	}

	/**
	 * The listener interface for receiving connection events. The class that is interested in processing a connection event implements this interface, and the object created with
	 * that class is registered with a component using the component's <code>addConnectionListener<code> method. When the connection event occurs, that object's appropriate method
	 * is invoked.
	 *
	 * @see ConnectionEvent
	 */
	protected class ConnectionListener implements ClientSessionChannel.MessageListener {

		/** The was connected. */
		private boolean	wasConnected;

		/** The connected. */
		private boolean	connected;

		/*
		 * (non-Javadoc)
		 * @see org.cometd.bayeux.client.ClientSessionChannel.MessageListener#onMessage(org.cometd.bayeux.client.ClientSessionChannel, org.cometd.bayeux.Message)
		 */
		@Override
		public void onMessage(ClientSessionChannel channel, Message message) {
			if (CometDCallbackClientHandler.this.getBayeuxClient().isDisconnected()) {
				this.connected = false;
				CometDCallbackClientHandler.this.connectionClosed();
				return;
			}

			this.wasConnected = this.connected;
			this.connected = message.isSuccessful();
			if (!this.wasConnected && this.connected) {
				CometDCallbackClientHandler.this.connectionEstablished();
			} else if (this.wasConnected && !this.connected) {
				CometDCallbackClientHandler.this.connectionBroken();
			}
		}
	}

	/**
	 * The listener interface for receiving message events. The class that is interested in processing a message event implements this interface, and the object created with that
	 * class is registered with a component using the component's <code>addMessageListener<code> method. When the message event occurs, that object's appropriate method is invoked.
	 *
	 * @see MessageEvent
	 */
	protected class MessageListener implements ClientSessionChannel.MessageListener {

		/*
		 * (non-Javadoc)
		 * @see org.cometd.bayeux.client.ClientSessionChannel.MessageListener#onMessage(org.cometd.bayeux.client.ClientSessionChannel, org.cometd.bayeux.Message)
		 */
		@Override
		public void onMessage(ClientSessionChannel channel, Message message) {
			try {
				CometDCallbackClientHandler.logger.debug("callback message received");
				CallbackWrapperMessage wrappedMessage = this.deserialize((String) message.getData());
				CometDCallbackClientHandler.this.fireMessageEvent(wrappedMessage);
			} catch (Exception error) {
				CometDCallbackClientHandler.logger.error(null, error);
			}
		}

		public CallbackWrapperMessage deserialize(String message) {
			try {
				String newMessage = new String(java.util.Base64.getDecoder().decode(message), StandardCharsets.ISO_8859_1);
				return new OntimizeMapper().readValue(newMessage, CallbackWrapperMessage.class);
			} catch (Exception error) {
				throw new OntimizeJEERuntimeException(error);
			}
		}
	}

	/**
	 * The Class OJettyCookieStore.
	 */
	private static class OJettyCookieStore implements CookieStore {
		/**
		 * The client cookies on this client .
		 */
		protected List<HttpCookie> cookies = new ArrayList<>();

		/*
		 * (non-Javadoc)
		 * @see java.net.CookieStore#getURIs()
		 */
		@Override
		public List<URI> getURIs() {
			return null;
		}

		/*
		 * (non-Javadoc)
		 * @see java.net.CookieStore#getCookies()
		 */
		@Override
		public List<HttpCookie> getCookies() {
			// Combine cookies from this store and the hessian store -> Ensure to seem a single client
			List<HttpCookie> res = new ArrayList<>();

			// 1º- Add hessian cookies (distinct cookieName, not equals, for instance ignoring domain)
			List<Cookie> hessianCookies = OntimizeHessianHttpClientSessionProcessorFactory.getCookieStore().getCookies();
			for (Cookie cookie : hessianCookies) {
				if (!this.checkCookieExists(res, cookie)) {
					res.add(new HttpCookie(cookie.getName(), cookie.getValue()));
				}
			}

			// 2º- Add my cookies (distinct cookieName, not equals, for instance ignoring domain)
			for (HttpCookie cookie : this.cookies) {
				if (!this.checkCookieExists(res, cookie)) {
					res.add(cookie);
				}
			}
			return res;
		}

		private boolean checkCookieExists(List<HttpCookie> cookieList, Cookie cookieToSearch) {
			if (cookieList != null) {
				for (HttpCookie cookie : cookieList) {
					if (ObjectTools.safeIsEquals(cookieToSearch.getName(), cookie.getName())) {
						return true;
					}
				}
			}
			return false;
		}

		private boolean checkCookieExists(List<HttpCookie> cookieList, HttpCookie cookieToSearch) {
			if (cookieList != null) {
				for (HttpCookie cookie : cookieList) {
					if (ObjectTools.safeIsEquals(cookieToSearch.getName(), cookie.getName())) {
						return true;
					}
				}
			}
			return false;
		}

		/*
		 * (non-Javadoc)
		 * @see java.net.CookieStore#get(java.net.URI)
		 */
		@Override
		public List<HttpCookie> get(URI uri) {
			return this.getCookies();
		}

		/*
		 * (non-Javadoc)
		 * @see java.net.CookieStore#add(java.net.URI, java.net.HttpCookie)
		 */
		@Override
		public void add(URI uri, HttpCookie cookie) {
			this.cookies.add(cookie);
		}

		/*
		 * (non-Javadoc)
		 * @see java.net.CookieStore#removeAll()
		 */
		@Override
		public boolean removeAll() {
			this.cookies.clear();
			return true;
		}

		/*
		 * (non-Javadoc)
		 * @see java.net.CookieStore#remove(java.net.URI, java.net.HttpCookie)
		 */
		@Override
		public boolean remove(URI uri, HttpCookie cookie) {
			return this.cookies.remove(cookie);
		}
	}
}
