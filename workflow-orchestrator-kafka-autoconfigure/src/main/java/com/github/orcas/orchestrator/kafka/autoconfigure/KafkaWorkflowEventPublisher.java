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
    private com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry = com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.noop();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTelemetry(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { this.telemetry = telemetry; }
    private static final Logger log = Logger.getLogger(KafkaWorkflowEventPublisher.class.getName());

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;
    private final boolean waitForAcknowledgement;

    public KafkaWorkflowEventPublisher(KafkaTemplate<String, String> k, ObjectMapper m, WorkflowKafkaProperties properties) {
        kafka = k;
        mapper = m;
        topic = properties.getTopic();
        waitForAcknowledgement = properties.isWaitForAcknowledgement();
    }

    /** Retains the original synchronous constructor for direct integrations. */
    public KafkaWorkflowEventPublisher(KafkaTemplate<String, String> k, ObjectMapper m, String topic) {
        kafka = k;
        mapper = m;
        this.topic = topic;
        waitForAcknowledgement = true;
    }

    public void publish(StatusEvent event) {
        var metadata = KafkaCorrelation.metadata(event.metadata(), null);
        var operation = telemetry.begin("kafkaEvent", "kafka.publish", metadata,
                java.util.Map.of("workflowId",event.workflowId(),"workflow",event.workflow(),"workflowStep",event.step(),"messaging.destination.name",topic));
        try {
            log.fine("Publishing status event step='" + event.step() + "' status=" + event.status() + " to topic '" + topic + "'");
            var enriched = new StatusEvent(event.workflowId(),event.workflow(),event.step(),event.status(),metadata.asMap(),event.message(),event.timestamp(),event.failure());
            var headers = operation.propagation();
            if (headers.isEmpty()) headers = com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.headers(metadata);
            var send = kafka.send(KafkaCorrelation.record(topic, event.workflowId(), mapper.writeValueAsString(enriched), headers));
            if (waitForAcknowledgement) {
                send.get(10, TimeUnit.SECONDS);
            } else {
                operation.detach();
                send.whenComplete((result, error) -> {
                    if (error != null) {
                        operation.error(error);
                        log.severe("Kafka failed to acknowledge workflow event for instance "
                                + event.workflowId() + " step '" + event.step() + "': " + error.getMessage());
                    }
                    operation.close();
                });
            }
        } catch (Exception e) {
            operation.error(e); operation.close();
            log.severe("Failed to publish status event for workflow instance " + event.workflowId() + " step '" + event.step() + "': " + e.getMessage());
            throw new IllegalStateException("Unable to publish workflow event", e);
        } finally {
            if (waitForAcknowledgement) operation.close();
        }
    }
}
