package com.ontimize.jee.server.callback.cometd;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.cometd.bayeux.Transport;
import org.cometd.bayeux.server.ServerTransport;
import org.cometd.server.AbstractServerTransport;
import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.transport.JSONPTransport;
import org.cometd.server.transport.JSONTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OntimizeBayeuxServerImpl extends BayeuxServerImpl {

	private static final Logger logger = LoggerFactory.getLogger(OntimizeBayeuxServerImpl.class);

	@Override
	protected void initializeServerTransports() {
		if (this.getTransports().isEmpty()) {
			this.initializeTransports();
		}

		if (this.getAllowedTransports().isEmpty()) {
			this.initializeAllowedTransports();
		}

		List<String> activeTransports = new ArrayList<>();
		for (String transportName : this.getAllowedTransports()) {
			ServerTransport serverTransport = this.getTransport(transportName);
			if (serverTransport instanceof AbstractServerTransport) {
				((AbstractServerTransport) serverTransport).init();
				activeTransports.add(serverTransport.getName());
			}
		}
		OntimizeBayeuxServerImpl.logger.debug("Active transports: {}", activeTransports);
	}

	protected void initializeAllowedTransports() {
		List<String> transportNames = new ArrayList<>();
		for (Transport transport : this.getTransports()) {
			transportNames.add(transport.getName());
		}
		String option = (String) this.getOption(BayeuxServerImpl.ALLOWED_TRANSPORTS_OPTION);
		if (option == null) {
			this.setAllowedTransports(transportNames);
		} else {
			List<String> allowedTransportNames = new ArrayList<>();
			for (String transportName : option.split(",")) {
				if (transportNames.contains(transportName)) {
					allowedTransportNames.add(transportName);
				}
			}
			this.setAllowedTransports(allowedTransportNames);

			if (this.getAllowedTransports().isEmpty()) {
				throw new IllegalArgumentException("Option '" + BayeuxServerImpl.ALLOWED_TRANSPORTS_OPTION + "' does not contain at least one configured server transport name");
			}
		}
	}

	protected void initializeTransports() {
		String option = (String) this.getOption(BayeuxServerImpl.TRANSPORTS_OPTION);
		if (option == null) {
			// Order is important, see #findHttpTransport()
			ServerTransport transport = this.newWebSocketTransport();
			if (transport != null) {
				this.addTransport(transport);
			}
			this.addTransport(this.newJSONTransport());
			this.addTransport(new JSONPTransport(this));
		} else {
			for (String className : option.split(",")) {
				ServerTransport transport = this.newServerTransport(className.trim());
				if (transport != null) {
					this.addTransport(transport);
				}
			}

			if (this.getTransports().isEmpty()) {
				throw new IllegalArgumentException("Option '" + BayeuxServerImpl.TRANSPORTS_OPTION + "' does not contain a valid list of server transport class names");
			}
		}
	}

	private ServerTransport newWebSocketTransport() {
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			loader.loadClass("javax.websocket.server.ServerContainer");
			String transportClass = "org.cometd.websocket.server.WebSocketTransport";
			ServerTransport transport = this.newServerTransport(transportClass);
			if (transport == null) {
				OntimizeBayeuxServerImpl.logger.info("JSR 356 WebSocket classes available, but " + transportClass + " unavailable: JSR 356 WebSocket transport disabled");
			}
			return transport;
		} catch (Exception x) {
			OntimizeBayeuxServerImpl.logger.debug(null, x);
			return null;
		}
	}

	private ServerTransport newJSONTransport() {
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			loader.loadClass("javax.servlet.ReadListener");
			return new OntimizeAsyncJSONTransport(this);
		} catch (Exception x) {
			OntimizeBayeuxServerImpl.logger.debug(null, x);
			return new JSONTransport(this);
		}
	}

	private ServerTransport newServerTransport(String className) {
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			@SuppressWarnings("unchecked")
			Class<? extends ServerTransport> klass = (Class<? extends ServerTransport>) loader.loadClass(className);
			Constructor<? extends ServerTransport> constructor = klass.getConstructor(BayeuxServerImpl.class);
			return constructor.newInstance(this);
		} catch (Exception x) {
			OntimizeBayeuxServerImpl.logger.error(null, x);
			return null;
		}
	}
}
