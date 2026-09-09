package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configures the {@link WorkflowEventConsumer} Kafka listener that feeds
 * consumed {@code StatusEvent}s into the {@link WorkflowEngine}.
 */
@AutoConfiguration
@AutoConfigureAfter({WorkflowCoreAutoConfiguration.class, WorkflowKafkaInfrastructureAutoConfiguration.class})
@ConditionalOnClass(KafkaTemplate.class)
public class WorkflowKafkaConsumerAutoConfiguration {
    @Bean(name = "workflowEventConsumer")
    @ConditionalOnMissingBean(name = "workflowEventConsumer")
    @ConditionalOnBean({WorkflowEngine.class, ConsumerFactory.class, ObjectMapper.class})
    WorkflowEventConsumer workflowEventConsumer(WorkflowEngine workflowEngine, ObjectMapper objectMapper) {
        return new WorkflowEventConsumer(workflowEngine, objectMapper);
    }
}
