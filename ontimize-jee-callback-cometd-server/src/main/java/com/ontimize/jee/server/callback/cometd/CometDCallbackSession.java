package com.ontimize.jee.server.callback.cometd;

import org.cometd.bayeux.server.ServerSession;

import com.ontimize.jee.server.callback.CallbackSession;

/**
 * The Class WebSocketCallbackSession.
 */
public class CometDCallbackSession implements CallbackSession {

	/** The native session. */
	private final Object nativeSession;

	/**
	 * Instantiates a new web socket callback session.
	 *
	 * @param session
	 *            the session
	 */
	public CometDCallbackSession(ServerSession session) {
		this.nativeSession = session;
	}

	/*
	 * (non-Javadoc)
	 * @see com.ontimize.jee.server.callback.CallbackSession#getNativeSession()
	 */
	@Override
	public Object getNativeSession() {
		return this.nativeSession;
	}

}
