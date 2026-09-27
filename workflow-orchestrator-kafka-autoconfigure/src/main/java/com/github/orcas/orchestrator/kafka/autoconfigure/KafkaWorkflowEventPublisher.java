package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * {@link WorkflowEventPublisher} that serializes {@link StatusEvent}s to JSON and
 * publishes them on the configured Kafka topic, keyed by the workflow instance id
 * (guaranteeing per-instance ordering within a partition).
 */
public final class KafkaWorkflowEventPublisher implements WorkflowEventPublisher {
    private static final Logger log = Logger.getLogger(KafkaWorkflowEventPublisher.class.getName());

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaWorkflowEventPublisher(KafkaTemplate<String, String> k, ObjectMapper m, String t) {
        kafka = k;
        mapper = m;
        topic = t;
    }

    public void publish(StatusEvent event) {
        try {
            log.fine("Publishing status event step='" + event.step() + "' status=" + event.status() + " to topic '" + topic + "'");
            kafka.send(topic, event.workflowId(), mapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.severe("Failed to publish status event for workflow instance " + event.workflowId() + " step '" + event.step() + "': " + e.getMessage());
            throw new IllegalStateException("Unable to publish workflow event", e);
        }
    }
}
