package com.github.orcas.orchestrator.autoconfigure.core;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowBeanResolverTest {
    @Test
    void resolvesConstructorInjectedResponseConsumerThroughSpring() {
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("dependency", new Dependency());
        factory.registerBeanDefinition(Consumer.class.getName(),
                org.springframework.beans.factory.support.BeanDefinitionBuilder
                        .genericBeanDefinition(Consumer.class)
                        .setAutowireMode(org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
                        .getBeanDefinition());

        var consumer = new WorkflowBeanResolver(factory).consumer(Consumer.class);

        assertThat(consumer).isInstanceOf(Consumer.class);
        assertThat(((Consumer) consumer).dependency).isNotNull();
    }

    @Test
    void resolvesStatelessDefaultsWithoutSpringRegistration() {
        var resolver = new WorkflowBeanResolver(new DefaultListableBeanFactory());

        assertThat(resolver.mapper(ContextMapper.Identity.class)).isInstanceOf(ContextMapper.Identity.class);
        assertThat(resolver.consumer(ResponseConsumer.Void.class)).isInstanceOf(ResponseConsumer.Void.class);
    }

    static final class Dependency {
    }

    static final class Consumer implements ResponseConsumer<Object> {
        private final Dependency dependency;

        Consumer(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public void consume(StepExecutionContext context, Object response) {
        }
    }
}
