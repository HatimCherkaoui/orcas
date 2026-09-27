package com.github.orcas.orchestrator.dashboard.autoconfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;

@AutoConfiguration
@AutoConfigureAfter(KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaAdmin.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.dashboard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowDashboardServiceAutoConfiguration {
    @Bean
    @ConditionalOnBean(KafkaAdmin.class)
    public KafkaServiceController kafkaServiceController(KafkaAdmin kafkaAdmin) {
        return new KafkaServiceController(kafkaAdmin);
    }
}
