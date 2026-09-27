package com.github.orcas.orchestrator.jdbc.autoconfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@AutoConfiguration
@AutoConfigureAfter({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnClass(NamedParameterJdbcTemplate.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowJdbcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public WorkflowServiceController workflowServiceController(NamedParameterJdbcTemplate jdbcTemplate) {
        return new WorkflowServiceController(jdbcTemplate);
    }
}
