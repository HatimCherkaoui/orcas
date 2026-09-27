package com.github.orcas.orchestrator.dashboard.autoconfigure;

import com.github.orcas.orchestrator.dashboard.api.KafkaService;
import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDashboardServiceAutoConfigurationContextTest {
    @Test
    void registersKafkaControllerWhenKafkaAdminIsAvailable() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KafkaAdmin.class, () -> new KafkaAdmin(Map.of(
                    CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")));
            context.register(WorkflowDashboardServiceAutoConfiguration.class);

            context.refresh();

            assertThat(context.getBean(KafkaService.class)).isNotNull();
            assertThat(context.getBean(KafkaServiceController.class)).isNotNull();
        }
    }
}
