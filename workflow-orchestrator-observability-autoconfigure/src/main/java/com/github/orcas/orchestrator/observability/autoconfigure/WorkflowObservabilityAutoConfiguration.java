package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowObserver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Installs the optional core lifecycle observer. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "workflow.orchestrator.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowObservabilityProperties.class)
public final class WorkflowObservabilityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WorkflowObserver.class)
    WorkflowObserver workflowObserver(WorkflowObservabilityProperties properties) {
        return new WorkflowMdcObserver(properties);
    }
}
