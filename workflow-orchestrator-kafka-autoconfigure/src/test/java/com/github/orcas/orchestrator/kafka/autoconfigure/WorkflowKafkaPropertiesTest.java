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
        assertThat(properties.getConcurrency()).isEqualTo(2);
        assertThat(properties.getTopicPartitions()).isEqualTo(12);
        assertThat(properties.getTopicReplicationFactor()).isEqualTo((short) 1);
        assertThat(properties.isWaitForAcknowledgement()).isTrue();
    }

    @Test
    void supportsReliabilityOverrides() {
        var properties = new WorkflowKafkaProperties();
        properties.setWaitForAcknowledgement(true);
        properties.setTopicPartitions(24);
        properties.setTopicReplicationFactor((short) 3);

        assertThat(properties.isWaitForAcknowledgement()).isTrue();
        assertThat(properties.getTopicPartitions()).isEqualTo(24);
        assertThat(properties.getTopicReplicationFactor()).isEqualTo((short) 3);
    }
}
