package com.github.orcas.orchestrator.kafka.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowKafkaContainerConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void appliesFetchLimitsToActualListenerContainers() {
        var properties = new WorkflowKafkaProperties();
        properties.setMaxPollRecords(7);
        properties.setFetchMaxBytes(524288);
        properties.setMaxPartitionFetchBytes(131072);
        var beans = new DefaultListableBeanFactory();
        var factory = new WorkflowKafkaAutoConfiguration().workflowKafkaListenerContainerFactory(
                (ConsumerFactory<String, String>) mock(ConsumerFactory.class),
                (KafkaTemplate<String, String>) mock(KafkaTemplate.class), properties,
                beans.getBeanProvider(ObservationRegistry.class));
        var first = factory.createContainer("workflow.status");
        var second = factory.createContainer("workflow.replay");
        for (var container : java.util.List.of(first, second)) {
            assertThat(container.getContainerProperties().getKafkaConsumerProperties())
                    .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 7)
                    .containsEntry(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 524288)
                    .containsEntry(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 131072);
        }
        assertThat(first.getContainerProperties().getKafkaConsumerProperties())
                .isNotSameAs(second.getContainerProperties().getKafkaConsumerProperties());
    }
}
