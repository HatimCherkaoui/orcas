package com.github.orcas.orchestrator.dashboard.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaServiceTest {
    @Test
    void exposesImmutableInspectionContracts() {
        var topic = new KafkaService.TopicInfo("workflow-events", 3, 2L);
        var group = new KafkaService.ConsumerGroupInfo("orcas", "Stable", Map.of("workflow-events", 2L));

        assertThat(topic.name()).isEqualTo("workflow-events");
        assertThat(topic.partitions()).isEqualTo(3);
        assertThat(List.of(topic, group)).hasSize(2);
    }
}
