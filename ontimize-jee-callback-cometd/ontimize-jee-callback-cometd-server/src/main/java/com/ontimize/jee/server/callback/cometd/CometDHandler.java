package com.ontimize.jee.server.callback.cometd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.cometd.annotation.Configure;
import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.ontimize.jee.common.callback.CallbackWrapperMessage;
import com.ontimize.jee.common.callback.cometd.CometDCallbackConstants;
import com.ontimize.jee.server.callback.CallbackSession;
import com.ontimize.jee.server.callback.ICallbackEventListener;
import com.ontimize.jee.server.callback.ICallbackHandler;

/**
 * The Class CometDHandler.
 */
@Component("CometDCallbackService")
@Lazy(value = true)
@Service("chat")
public class CometDHandler implements ICallbackHandler {

	/** The Constant ATTRIBUTE_PRINCIPAL. */
	private static final String ATTRIBUTE_PRINCIPAL = "principal";

	/** The Constant logger. */
	private static final Logger					logger							= LoggerFactory.getLogger(CometDHandler.class);

	/** The bayeux server. */
	@Inject
	private BayeuxServer						bayeuxServer;

	/** The server session. */
	@Session
	private ServerSession						serverSession;
	/** The message listeners. */
	private final List<ICallbackEventListener>	messageListeners;

	/** The members. */
	private final List<String>					members;

	/**
	 * Instantiates a new comet D handler.
	 */
	public CometDHandler() {
		super();
		this.members = new ArrayList<>();
		this.messageListeners = new ArrayList<>();
	}

	/**
	 * Configure members.
	 *
	 * @param channel
	 *            the channel
	 */
	@Configure(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL)
	protected void configureMembers(ConfigurableServerChannel channel) {
		// channel.addAuthorizer(GrantAuthorizer.GRANT_PUBLISH);
		channel.setPersistent(true);
	}

	/**
	 * Handle membership.
	 *
	 * @param client
	 *            the client
	 * @param message
	 *            the message
	 */
	@Listener(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL)
	public void handleMembership(ServerSession client, ServerMessage message) {
		Map<String, Object> data = message.getDataAsMap();
		if (CometDCallbackConstants.ACTION_REGISTER.equals(data.get(CometDCallbackConstants.KEY_ACTION))) {
			this.register(client, message, data);
		} else {
			CallbackWrapperMessage wrappedMessage = CallbackWrapperMessage.deserialize((String) data.get(CometDCallbackConstants.KEY_DATA));
			this.fireMessageReceived(new CometDCallbackSession(client), wrappedMessage);
		}

	}

	/**
	 * Register.
	 *
	 * @param client
	 *            the client
	 * @param message
	 *            the message
	 * @param data
	 *            the data
	 */
	private void register(ServerSession client, ServerMessage message, Map<String, Object> data) {
		this.registerMember(client);
		client.addListener(new ServerSession.RemoveListener() {
			@Override
			public void removed(ServerSession session, boolean timeout) {
				CometDHandler.this.unregisterMember(session);
			}
		});
	}

	/**
	 * Register member.
	 *
	 * @param client
	 *            the client
	 */
	protected void registerMember(ServerSession client) {
		client.setAttribute(CometDHandler.ATTRIBUTE_PRINCIPAL, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName());
		CometDHandler.logger.info("Callback registering for principal {}", client.getAttribute(CometDHandler.ATTRIBUTE_PRINCIPAL));
		synchronized (this.members) {
			this.members.add(client.getId());
		}
	}

	/**
	 * Unregister member.
	 *
	 * @param client
	 *            the client
	 */
	protected void unregisterMember(ServerSession client) {
		CometDHandler.logger.info("Callback UNregistering for principal {}", client.getAttribute(CometDHandler.ATTRIBUTE_PRINCIPAL));
		synchronized (CometDHandler.this.members) {
			CometDHandler.this.members.remove(client.getId());
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.server.callback.ICallbackHandler#addCallbackEventListener(com.ontimize.jee.server.callback.ICallbackEventListener)
	 */
	@Override
	public void addCallbackEventListener(ICallbackEventListener listener) {
		synchronized (this.messageListeners) {
			this.messageListeners.add(listener);
		}
	}

	/**
	 * Fire message received.
	 *
	 * @param session
	 *            the session
	 * @param message
	 *            the message
	 */
	protected void fireMessageReceived(CometDCallbackSession session, CallbackWrapperMessage message) {
		synchronized (this.messageListeners) {
			for (ICallbackEventListener listener : this.messageListeners) {
				listener.onCallbackMessageReceived(session, message);
			}
		}
	}

	/**
	 * Send message.
	 *
	 * @param messageType
	 *            the message type
	 * @param messageSubtype
	 *            the message subtype
	 * @param ob
	 *            the ob
	 * @param receivers
	 *            the receivers
	 */
	@Override
	public void sendMessage(Integer messageType, String messageSubtype, Object ob, CallbackSession... receivers) {
		ServerMessage.Mutable textMessage = this.buildTextMessage(messageType, messageSubtype, ob);
		for (CallbackSession session : receivers) {
			this.getCometDSession(session).deliver(this.serverSession, textMessage);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.server.callback.ICallbackHandler#sendMessage(java.lang.Integer, java.lang.String, java.lang.Object, com.ontimize.jee.server.callback.CallbackSession)
	 */
	@Override
	public void sendMessage(Integer messageType, String messageSubtype, Object ob, CallbackSession receiver) throws IOException {
		ServerMessage.Mutable textMessage = this.buildTextMessage(messageType, messageSubtype, ob);
		this.getCometDSession(receiver).deliver(this.serverSession, textMessage);
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.server.callback.ICallbackHandler#sendBroadcastMessage(java.lang.Integer, java.lang.String, java.lang.Object)
	 */
	@Override
	public void sendBroadcastMessage(Integer messageType, String messageSubtype, Object ob) {
		ServerMessage.Mutable textMessage = this.buildTextMessage(messageType, messageSubtype, ob);
		for (String peerId : this.members) {
			ServerSession peer = this.bayeuxServer.getSession(peerId);
			peer.deliver(this.serverSession, textMessage);
		}
	}


	/**
	 * Builds the text message.
	 *
	 * @param messageType
	 *            the message type
	 * @param messageSubtype
	 *            the message subtype
	 * @param ob
	 *            the ob
	 * @return the server message. mutable
	 */
	protected ServerMessage.Mutable buildTextMessage(Integer messageType, String messageSubtype, Object ob) {
		ServerMessage.Mutable msg = this.bayeuxServer.newMessage();
		msg.setChannel(CometDCallbackConstants.ONTIMIZE_JEE_CALLBACK_CHANNEL);
		msg.setData(new CallbackWrapperMessage(messageType, messageSubtype, ob).serialize());
		msg.setLazy(false);
		return msg;
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.server.callback.ICallbackHandler#getSessionsForUser(java.lang.String)
	 */
	@Override
	public List<CallbackSession> getSessionsForUser(String userLogin) {
		List<CallbackSession> res = new ArrayList<>();
		if (userLogin == null) {
			return res;
		}

		for (String peerId : this.members) {
			ServerSession peer = this.bayeuxServer.getSession(peerId);
			if (userLogin.equals(peer.getAttribute(CometDHandler.ATTRIBUTE_PRINCIPAL))) {
				res.add(new CometDCallbackSession(peer));
			}
		}
		return res;
	}

	/**
	 * Gets the web socket session.
	 *
	 * @param session
	 *            the session
	 * @return the web socket session
	 */
	protected ServerSession getCometDSession(CallbackSession session) {
		return (ServerSession) session.getNativeSession();
	}
}
