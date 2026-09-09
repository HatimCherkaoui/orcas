package com.github.orcas.orchestrator.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Registers the {@code @ConfigurationProperties} beans exposed by this starter so
 * they are bound and validated regardless of which other auto-configuration classes
 * are activated.
 */
@AutoConfiguration
@EnableConfigurationProperties({WorkflowProperties.class, WorkflowRetryProperties.class, WorkflowCircuitBreakerProperties.class})
public class WorkflowPropertiesAutoConfiguration {
}
