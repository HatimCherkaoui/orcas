package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/** JDBC persistence integration; it replaces the core in-memory store when a DataSource exists. */
@AutoConfiguration(after = {
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class
}, afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnClass({NamedParameterJdbcTemplate.class, ObjectMapper.class})
@ConditionalOnProperty(prefix = "workflow.orchestrator.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowJdbcProperties.class)
public final class WorkflowJdbcAutoConfiguration {
    @Bean
    @ConditionalOnSingleCandidate(DataSource.class)
    @ConditionalOnMissingBean
    NamedParameterJdbcTemplate workflowJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnSingleCandidate(DataSource.class)
    @ConditionalOnMissingBean(com.github.orcas.orchestrator.core.engine.WorkflowStateStore.class)
    JdbcWorkflowStateStore workflowJdbcStateStore(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        return new JdbcWorkflowStateStore(jdbc, mapper, transactionManager);
    }

    @Bean
    @ConditionalOnSingleCandidate(DataSource.class)
    static WorkflowJdbcSchemaInitializer workflowJdbcSchemaInitializer(
            DataSource dataSource,
            WorkflowJdbcProperties properties) {
        return new WorkflowJdbcSchemaInitializer(dataSource, properties);
    }
}
