package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryProperties;
import com.github.orcas.orchestrator.service.api.KafkaService;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.engine.WorkflowRegistry;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.jdbc.JdbcWorkflowAdminService;
import com.github.orcas.orchestrator.service.jdbc.JdbcWorkflowQueryService;
import com.github.orcas.orchestrator.service.kafka.DefaultKafkaService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Auto-configures the admin/query REST API exposed by this starter: JDBC-backed
 * {@link WorkflowQueryService} and {@link WorkflowAdminService} implementations, the
 * optional Kafka introspection service, and the REST controllers themselves. Active
 * whenever {@code workflow.orchestrator.service.enabled} is {@code true} (default).
 */
@AutoConfiguration
@EnableConfigurationProperties(WorkflowServiceProperties.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.service", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({NamedParameterJdbcTemplate.class, ObjectMapper.class})
    WorkflowQueryService workflowQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        return new JdbcWorkflowQueryService(jdbc, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({NamedParameterJdbcTemplate.class, ObjectMapper.class, WorkflowEngine.class})
    WorkflowAdminService workflowAdminService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, WorkflowEngine engine) {
        return new JdbcWorkflowAdminService(jdbc, mapper, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(KafkaAdmin.class)
    @ConditionalOnBean(KafkaAdmin.class)
    @ConditionalOnProperty(prefix = "workflow.orchestrator.service.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    KafkaService kafkaService(KafkaAdmin admin, WorkflowServiceProperties properties) {
        return new DefaultKafkaService(admin, Duration.ofSeconds(properties.getKafka().getTimeoutSeconds()));
    }
    @Bean
    @ConditionalOnMissingBean(WorkflowServiceController.class)
    @ConditionalOnBean({WorkflowQueryService.class, WorkflowAdminService.class})
    WorkflowServiceController workflowServiceController(
            WorkflowQueryService query,
            WorkflowAdminService admin, WorkflowRegistry registry,
            WorkflowRetryProperties retryProperties) {
        return new WorkflowServiceController(query, admin, registry, retryProperties);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaServiceController.class)
    @ConditionalOnBean(KafkaService.class)
    KafkaServiceController kafkaServiceController(KafkaService kafka) {
        return new KafkaServiceController(kafka);
    }
}
