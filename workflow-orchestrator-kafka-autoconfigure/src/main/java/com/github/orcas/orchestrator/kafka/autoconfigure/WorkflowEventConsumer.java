package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;


/**
 * Kafka listener that feeds every {@link StatusEvent} published on the orchestrator
 * topic back into the {@link WorkflowEngine}, closing the publish/consume loop that
 * drives workflow progression.
 */
public final class WorkflowEventConsumer {
    private com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry = com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.noop();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTelemetry(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { this.telemetry = telemetry; }

    private final ObjectProvider<WorkflowEngine> engine;
    private final ObjectMapper mapper;

    public WorkflowEventConsumer(ObjectProvider<WorkflowEngine> e, ObjectMapper m) {
        engine = e;
        mapper = m;
    }

    @KafkaListener(
            topics = "${workflow.orchestrator.kafka.topic}",
            groupId = "${workflow.orchestrator.kafka.consumer-group}",
            containerFactory = "workflowKafkaListenerContainerFactory")
    public void onRecord(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record) throws Exception {
        consume(record.value(),record.headers());
    }

    public void onMessage(String message) throws Exception { consume(message,null); }

    private void consume(String message, org.apache.kafka.common.header.Headers headers) throws Exception {
        StatusEvent event = mapper.readValue(message, StatusEvent.class);
        var metadata = KafkaCorrelation.metadata(event.metadata(), headers);
        try (var operation = telemetry.begin("kafkaEvent", "kafka.consume", metadata,
                java.util.Map.of("workflowId",event.workflowId(),"workflow",event.workflow(),"workflowStep",event.step()))) {
            try { engine.getObject().handle(event); }
            catch (Exception error) { operation.error(error); throw error; }
        }
    }
}
