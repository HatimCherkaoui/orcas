package com.github.orcas.orchestrator.dashboard.autoconfigure;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDashboardServiceAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    WorkflowDashboardServiceAutoConfiguration.class
            ));

    @Test
    void shouldAutoConfigureKafkaServiceControllerWhenKafkaAdminPresent() {
        this.contextRunner
                .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092")
                .run(context -> assertThat(context).hasSingleBean(KafkaServiceController.class));
    }
}
