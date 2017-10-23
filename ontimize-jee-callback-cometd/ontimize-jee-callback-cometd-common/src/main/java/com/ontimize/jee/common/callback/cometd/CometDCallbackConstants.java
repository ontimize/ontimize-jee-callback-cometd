package com.ontimize.jee.common.callback.cometd;

/**
 * The Class CometDCallbackConstants.
 */
public class CometDCallbackConstants {

	/** The Constant ONTIMIZE_JEE_CALLBACK_CHANNEL. */
	public static final String	ONTIMIZE_JEE_CALLBACK_CHANNEL	= "/service/ontimize-jee-callback";

	/** The Constant KEY_DATA. */
	public static final String	KEY_DATA						= "key_data";

	/** The Constant KEY_ACTION. */
	public static final String	KEY_ACTION						= "key_action";

	/** The Constant ACTION_REGISTER. */
	public static final String	ACTION_REGISTER					= "key_register";

	public static final Object	ACTION_MESSAGE					= "key_message";

	private CometDCallbackConstants() {
		// empty constructor
	}
}
