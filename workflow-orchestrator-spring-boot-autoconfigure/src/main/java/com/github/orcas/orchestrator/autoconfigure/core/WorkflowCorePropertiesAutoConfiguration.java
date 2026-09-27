package com.github.orcas.orchestrator.autoconfigure.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Registers only properties owned by the core Spring integration. */
@AutoConfiguration
@EnableConfigurationProperties(WorkflowAsyncProperties.class)
public final class WorkflowCorePropertiesAutoConfiguration {
}
