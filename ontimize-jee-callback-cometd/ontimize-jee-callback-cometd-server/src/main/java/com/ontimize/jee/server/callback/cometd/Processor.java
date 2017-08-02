package com.ontimize.jee.server.callback.cometd;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.ServerAnnotationProcessor;
import org.cometd.bayeux.server.BayeuxServer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class Processor implements DestructionAwareBeanPostProcessor {
	@Autowired
	private BayeuxServer				bayeuxServer;
	private ServerAnnotationProcessor	processor;

	@PostConstruct
	private void init() {
		this.processor = new ServerAnnotationProcessor(this.bayeuxServer);
	}

	@PreDestroy
	private void destroy() {}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String name) throws BeansException {
		this.processor.processDependencies(bean);
		this.processor.processConfigurations(bean);
		this.processor.processCallbacks(bean);
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
		return bean;
	}

	@Override
	public void postProcessBeforeDestruction(Object bean, String name) throws BeansException {
		this.processor.deprocessCallbacks(bean);
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		return true;
	}
}