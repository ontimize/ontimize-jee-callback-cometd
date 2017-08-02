package com.ontimize.jee.desktopclient.callback.cometd;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.ontimize.jee.common.callback.CallbackWrapperMessage;
import com.ontimize.jee.common.callback.cometd.CometDCallbackConstants;
import com.ontimize.jee.common.exceptions.OntimizeJEEException;
import com.ontimize.jee.common.hessian.OntimizeHessianHttpClientSessionProcessorFactory;
import com.ontimize.jee.common.hessian.OntimizeHessianProxyFactoryBean;
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
	protected final MessageListener				chatListener			= new MessageListener();
	// private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
	/** The callback relative url. */
	// private final WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
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
		// new Thread("") {
		// @Override
		// public void run() {
		try {
			CometDCallbackClientHandler.this.connect();
		} catch (Exception error) {
			CometDCallbackClientHandler.logger.error("Error connecting callback cometd. WONT BE RETRIED", error);
		}
		// }
		// }.start();
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.desktopclient.callback.ICallbackClientHandler#addWebSocketEventListener(com.ontimize.jee.desktopclient.callback.ICallbackEventListener)
	 */
	@Override
	public void addWebSocketEventListener(ICallbackEventListener listener) {
		this.listeners.add(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.desktopclient.callback.ICallbackClientHandler#removeWebSocketEventListener(com.ontimize.jee.desktopclient.callback.ICallbackEventListener)
	 */
	@Override
	public void removeWebSocketEventListener(ICallbackEventListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Connect.
	 *
	 * @throws Exception
	 *             the exception
	 */
	protected void connect() throws Exception {
		HttpClient httpClient = new HttpClient();
		httpClient.start();
		httpClient.setCookieStore(new CookieStore() {

			@Override
			public boolean removeAll() {
				return false;
			}

			@Override
			public boolean remove(URI uri, HttpCookie cookie) {
				System.out.println("remove");
				return false;
			}

			@Override
			public List<URI> getURIs() {
				System.out.println("geturis");
				return null;
			}

			@Override
			public List<HttpCookie> getCookies() {
				System.out.println("getcookies");
				List<Cookie> cookies = OntimizeHessianHttpClientSessionProcessorFactory.getCookieStore().getCookies();
				List<HttpCookie> res = new ArrayList<>();
				for (Cookie cookie : cookies) {
					res.add(new HttpCookie(cookie.getName(), cookie.getValue()));
				}
				return res;
			}

			@Override
			public List<HttpCookie> get(URI uri) {
				System.out.println("geturi");
				return this.getCookies();
			}

			@Override
			public void add(URI uri, HttpCookie cookie) {
				System.out.println("add");
			}
		});

		this.setBayeuxClient(new BayeuxClient(this.getCallbackUrl(), new LongPollingTransport(null, httpClient) {
			@Override
			protected void customize(Request request) {
				super.customize(request);
				if (!StringTools.isEmpty((OntimizeHessianHttpClientSessionProcessorFactory.JWT_TOKEN))) {
					request.header("Authorization", "Bearer " + OntimizeHessianHttpClientSessionProcessorFactory.JWT_TOKEN);
				}
			}
		}));
		// Map<String, Object> options = new HashMap<>();
		// options.put(ClientTransport.JSON_CONTEXT_OPTION, new JacksonJSONContextClient());
		// options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, ConsoleChatClient.MAX_NETWORK_DELAY);
		// // Differently from HTTP where the idle timeout is adjusted if it is a /meta/connect
		// // for WebSocket we need an idle timeout that is longer than the /meta/connect timeout.
		// options.put(AbstractWebSocketTransport.IDLE_TIMEOUT_OPTION, ConsoleChatClient.META_CONNECT_TIMEOUT + httpClient.getIdleTimeout());
		// this.client = new BayeuxClient(url, new WebSocketTransport(options, this.scheduler, this.webSocketContainer));

		this.getBayeuxClient().getChannel(Channel.META_HANDSHAKE).addListener(new InitializerListener());
		this.getBayeuxClient().getChannel(Channel.META_CONNECT).addListener(new ConnectionListener());

		this.getBayeuxClient().handshake();
	}

	/**
	 * Initialize.
	 */
	protected void initialize() {
		this.getBayeuxClient().batch(new Runnable() {
			@Override
			public void run() {
				ClientSessionChannel chatChannel = CometDCallbackClientHandler.this.getBayeuxClient().getChannel(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL);
				chatChannel.subscribe(CometDCallbackClientHandler.this.chatListener);
			}
		});
	}

	/**
	 * Connection established.
	 */
	protected void connectionEstablished() {
		CometDCallbackClientHandler.logger.info("system: Connection to Server Opened");
		Map<String, Object> data = new HashMap<>();
		data.put(CometDCallbackConstants.KEY_ACTION, CometDCallbackConstants.ACTION_REGISTER);
		this.bayeuxClient.getChannel(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL).publish(data);
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
	 * The listener interface for receiving initializer events. The class that is interested in processing a initializer event implements this interface, and the object created
	 * with that class is registered with a component using the component's <code>addInitializerListener<code> method. When the initializer event occurs, that object's appropriate
	 * method is invoked.
	 *
	 * @see InitializerEvent
	 */
	protected class InitializerListener implements ClientSessionChannel.MessageListener {

		/*
		 * (non-Javadoc)
		 * @see org.cometd.bayeux.client.ClientSessionChannel.MessageListener#onMessage(org.cometd.bayeux.client.ClientSessionChannel, org.cometd.bayeux.Message)
		 */
		@Override
		public void onMessage(ClientSessionChannel channel, Message message) {
			if (message.isSuccessful()) {
				CometDCallbackClientHandler.this.initialize();
			}
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
			CometDCallbackClientHandler.logger.debug("callback message received");
			CallbackWrapperMessage wrappedMessage = CallbackWrapperMessage.deserialize((String) message.getData());
			CometDCallbackClientHandler.this.fireMessageEvent(wrappedMessage);

		}
	}
}
