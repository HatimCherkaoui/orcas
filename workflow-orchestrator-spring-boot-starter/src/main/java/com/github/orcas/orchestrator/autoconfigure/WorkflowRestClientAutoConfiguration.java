package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClientProperties;
import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClientRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * Auto-configures REST-client support: binds {@link WorkflowRestClientProperties} and
 * registers the {@link WorkflowRestClientRegistrar} that discovers
 * {@code @WorkflowRestClient} interfaces and creates their dynamic proxies.
 */
@AutoConfiguration
@AutoConfigureAfter(WorkflowCoreAutoConfiguration.class)
public class WorkflowRestClientAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConfigurationProperties(prefix = "workflow.orchestrator.rest-client")
    WorkflowRestClientProperties workflowRestClientProperties() {
        return new WorkflowRestClientProperties();
    }

    @Bean
    static WorkflowRestClientRegistrar workflowRestClientRegistrar(
            org.springframework.beans.factory.BeanFactory beanFactory) {
        return new WorkflowRestClientRegistrar(beanFactory);
    }
}
