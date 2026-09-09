package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link WorkflowEventPublisher} that serializes {@link StatusEvent}s to JSON and
 * publishes them on the configured Kafka topic, keyed by the workflow instance id
 * (guaranteeing per-instance ordering within a partition).
 */
public final class KafkaWorkflowEventPublisher implements WorkflowEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaWorkflowEventPublisher.class);

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
            log.debug("Publishing status event step='{}' status={} to topic '{}'", event.step(), event.status(), topic);
            kafka.send(topic, event.workflowId(), mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish status event for workflow instance {} step '{}'", event.workflowId(), event.step(), e);
            throw new IllegalStateException("Unable to publish workflow event", e);
        }
    }
}
