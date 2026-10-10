package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.core.WorkflowCoreAutoConfiguration;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import tools.jackson.databind.ObjectMapper;

/** Kafka consumers that feed runtime events back into the workflow engine. */
@AutoConfiguration(after = WorkflowCoreAutoConfiguration.class,
        afterName = "com.github.orcas.orchestrator.kafka.autoconfigure.WorkflowKafkaAutoConfiguration")
@ConditionalOnClass(WorkflowEngine.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class WorkflowKafkaConsumerAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "workflow.orchestrator.kafka", name = "consumers-enabled", havingValue = "true", matchIfMissing = true)
    WorkflowReplayCommandConsumer workflowReplayCommandConsumer(
            ObjectProvider<WorkflowEngine> engine,
            ObjectMapper mapper,
            @Qualifier("workflowKafkaListenerContainerFactory") ConcurrentKafkaListenerContainerFactory<String, String> ignored) {
        return new WorkflowReplayCommandConsumer(engine, mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "workflow.orchestrator.kafka", name = "consumers-enabled", havingValue = "true", matchIfMissing = true)
    WorkflowEventConsumer workflowEventConsumer(
            ObjectProvider<WorkflowEngine> engine,
            ObjectMapper mapper,
            @Qualifier("workflowKafkaListenerContainerFactory") ConcurrentKafkaListenerContainerFactory<String, String> ignored) {
        return new WorkflowEventConsumer(engine, mapper);
    }
}
