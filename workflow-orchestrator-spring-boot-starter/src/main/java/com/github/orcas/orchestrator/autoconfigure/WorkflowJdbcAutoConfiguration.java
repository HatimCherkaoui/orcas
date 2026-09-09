package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowStateStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/**
 * Auto-configures the durable {@link WorkflowStateStore} JDBC implementation and, when
 * enabled via {@code workflow.orchestrator.persistence.schema-initialization}, applies
 * the bundled {@code orchestrator-schema.sql} script on startup.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration", "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration", "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"})
@ConditionalOnClass({DataSource.class, NamedParameterJdbcTemplate.class})
public class WorkflowJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkflowStateStore.class)
    @ConditionalOnBean({DataSource.class, NamedParameterJdbcTemplate.class, ObjectMapper.class, PlatformTransactionManager.class})
    WorkflowStateStore workflowStateStore(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        return new JdbcWorkflowStateStore(jdbcTemplate, objectMapper, transactionManager);
    }

    @Bean(name = "workflowSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = "workflowSchemaInitializer")
    @ConditionalOnProperty(
            name = "workflow.orchestrator.persistence.schema-initialization",
            havingValue = "true",
            matchIfMissing = true)
    DataSourceInitializer workflowSchemaInitializer(
            DataSource dataSource,
            WorkflowProperties properties) {
        var populator = new ResourceDatabasePopulator();
        populator.addScript(new DefaultResourceLoader().getResource(
                properties.getPersistence().getSchemaLocation()));
        var initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
