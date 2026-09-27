package com.github.orcas.orchestrator.starter;
import com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowJdbcAutoConfiguration;
import com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowServiceController;
import com.github.orcas.orchestrator.dashboard.autoconfigure.WorkflowDashboardServiceAutoConfiguration;
import com.github.orcas.orchestrator.dashboard.autoconfigure.KafkaServiceController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class UnifiedStarterIntegrationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    KafkaAutoConfiguration.class,
                    WorkflowJdbcAutoConfiguration.class,
                    WorkflowDashboardServiceAutoConfiguration.class
            ));

    @Test
    void shouldBootAllServicesSmoothlyWithUnifiedStarter() {
        this.contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:starterdb",
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "workflow.orchestrator.jdbc.enabled=true",
                        "workflow.orchestrator.dashboard.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkflowServiceController.class);
                    assertThat(context).hasSingleBean(KafkaServiceController.class);
                });
    }
}
