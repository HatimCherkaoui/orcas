package com.github.orcas.orchestrator.kafka.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowKafkaPropertiesTest {
    @Test
    void usesOperationalDefaults() {
        var properties = new WorkflowKafkaProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTopic()).isEqualTo("workflow.status");
        assertThat(properties.getConsumerGroup()).isEqualTo("workflow-orchestrator");
        assertThat(properties.getConcurrency()).isEqualTo(1);
    }
}
