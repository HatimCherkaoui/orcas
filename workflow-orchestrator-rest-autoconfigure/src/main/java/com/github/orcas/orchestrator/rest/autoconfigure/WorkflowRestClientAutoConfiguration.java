package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.core.WorkflowCoreAutoConfiguration;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/** Declarative WebClient integration for workflow steps and HTTP launches. */
@AutoConfiguration(after = WorkflowCoreAutoConfiguration.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ConditionalOnClass(org.springframework.web.service.invoker.HttpServiceProxyFactory.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.rest-client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowRestClientProperties.class)
public final class WorkflowRestClientAutoConfiguration {
    @Bean
    static WorkflowRestClientRegistrar workflowRestClientRegistrar(BeanFactory beanFactory) {
        return new WorkflowRestClientRegistrar(beanFactory);
    }

    @Bean
    static WorkflowLaunchAspect workflowLaunchAspect(WorkflowEngine engine) {
        return new WorkflowLaunchAspect(engine);
    }
}
