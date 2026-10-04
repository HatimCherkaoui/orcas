package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/** JDBC persistence integration; it replaces the core in-memory store when a DataSource exists. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkflowJdbcProperties.class)
public final class WorkflowJdbcAutoConfiguration {
    @Bean
    NamedParameterJdbcTemplate workflowJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    JdbcWorkflowStateStore workflowJdbcStateStore(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        return new JdbcWorkflowStateStore(jdbc, mapper, transactionManager);
    }

    @Bean
    static WorkflowJdbcSchemaInitializer workflowJdbcSchemaInitializer(
            DataSource dataSource,
            WorkflowJdbcProperties properties) {
        return new WorkflowJdbcSchemaInitializer(dataSource, properties);
    }
}
