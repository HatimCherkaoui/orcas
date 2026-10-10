package com.github.orcas.orchestrator.dashboard.autoconfigure;

import com.github.orcas.orchestrator.dashboard.api.KafkaService;
import com.github.orcas.orchestrator.dashboard.autoconfigure.kafka.DefaultKafkaService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;

/** Optional Kafka introspection API used by the dashboard. */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaAdmin.class)
@ConditionalOnBean(KafkaAdmin.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.dashboard", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowDashboardServiceProperties.class)
public final class WorkflowDashboardServiceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    KafkaService workflowKafkaDashboardService(KafkaAdmin admin, WorkflowDashboardServiceProperties properties) {
        return new DefaultKafkaService(admin, java.time.Duration.ofSeconds(properties.getTimeoutSeconds()));
    }

    @Bean
    @ConditionalOnMissingBean
    KafkaServiceController workflowKafkaServiceController(KafkaService kafka) {
        return new KafkaServiceController(kafka);
    }
}
