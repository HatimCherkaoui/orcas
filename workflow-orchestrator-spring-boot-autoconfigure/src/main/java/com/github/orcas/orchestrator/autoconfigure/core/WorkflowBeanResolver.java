package com.github.orcas.orchestrator.autoconfigure.core;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import org.springframework.beans.factory.BeanFactory;

/** Resolves user extension types through Spring IoC instead of reflection. */
public final class WorkflowBeanResolver {
    private final BeanFactory beanFactory;

    public WorkflowBeanResolver(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public <T> T get(Class<T> type) {
        return beanFactory.getBean(type);
    }

    @SuppressWarnings("unchecked")
    public <T> ContextMapper<T> mapper(Class<? extends ContextMapper<?>> type) {
        return (ContextMapper<T>) (type == ContextMapper.Identity.class
                ? new ContextMapper.Identity()
                : beanFactory.getBean(type));
    }

    public ResponseConsumer<?> consumer(Class<? extends ResponseConsumer<?>> type) {
        return type == ResponseConsumer.Void.class
                ? new ResponseConsumer.Void()
                : beanFactory.getBean(type);
    }
}
