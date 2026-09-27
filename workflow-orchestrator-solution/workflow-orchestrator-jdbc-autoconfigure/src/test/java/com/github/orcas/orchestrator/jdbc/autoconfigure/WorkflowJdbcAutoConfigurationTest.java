package com.github.orcas.orchestrator.jdbc.autoconfigure;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowJdbcAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    WorkflowJdbcAutoConfiguration.class
            ));

    @Test
    void shouldAutoConfigureWorkflowServiceControllerWhenDataSourcePresent() {
        this.contextRunner
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:testdb", "spring.datasource.driver-class-name=org.h2.Driver")
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkflowServiceController.class);
                    assertThat(context).hasSingleBean(NamedParameterJdbcTemplate.class);
                });
    }
}
