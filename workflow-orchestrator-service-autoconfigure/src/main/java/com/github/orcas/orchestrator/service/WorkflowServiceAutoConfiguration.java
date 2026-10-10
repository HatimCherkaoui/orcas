package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.api.WorkflowReplayPublisher;
import com.github.orcas.orchestrator.service.jdbc.JdbcWorkflowAdminService;
import com.github.orcas.orchestrator.service.jdbc.JdbcWorkflowQueryService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Optional query/admin REST API over workflow state. */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@ConditionalOnClass({NamedParameterJdbcTemplate.class, ObjectMapper.class, org.springframework.web.bind.annotation.RestController.class})
@ConditionalOnBean(NamedParameterJdbcTemplate.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.service", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowServiceProperties.class)
public final class WorkflowServiceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WorkflowQueryService.class)
    WorkflowQueryService workflowQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        return new JdbcWorkflowQueryService(jdbc, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowAdminService.class)
    WorkflowAdminService workflowAdminService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
                                              ObjectProvider<WorkflowReplayPublisher> replayPublisherProvider) {
        return new JdbcWorkflowAdminService(jdbc, mapper, replayPublisherProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowServiceController.class)
    WorkflowServiceController workflowServiceController(WorkflowQueryService query,
                                                        WorkflowAdminService admin) {
        return new WorkflowServiceController(query, admin);
    }
}
