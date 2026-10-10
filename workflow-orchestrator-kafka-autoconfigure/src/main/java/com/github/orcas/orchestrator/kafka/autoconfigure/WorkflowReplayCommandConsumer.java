package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.WorkflowReplayCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.logging.Logger;

/** Receives operator replay commands in the workflow runtime application. */
public final class WorkflowReplayCommandConsumer {
    private com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry = com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.noop();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTelemetry(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { this.telemetry = telemetry; }

    private static final Logger log = Logger.getLogger(WorkflowReplayCommandConsumer.class.getName());

    private final ObjectProvider<WorkflowEngine> engine;
    private final ObjectMapper mapper;

    public WorkflowReplayCommandConsumer(ObjectProvider<WorkflowEngine> engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "${workflow.orchestrator.kafka.replay-topic:workflow.replay}",
            groupId = "${workflow.orchestrator.kafka.replay-consumer-group:workflow-orchestrator-replay}",
            containerFactory = "workflowKafkaListenerContainerFactory")
    public void onRecord(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record) throws Exception {
        consume(record.value(),record.headers());
    }

    public void onMessage(String message) throws Exception { consume(message,null); }

    private void consume(String message, org.apache.kafka.common.header.Headers headers) throws Exception {
        var command = mapper.readValue(message, WorkflowReplayCommand.class);
        log.info("Replaying workflowId='" + command.workflowId() + "' step='" + command.stepName() + "'");
        var metadata = KafkaCorrelation.metadata(command.metadata(),headers);
        try (var operation = telemetry.begin("kafkaEvent", "kafka.replay.consume", metadata,
                java.util.Map.of("workflowId",command.workflowId(),"workflowStep",command.stepName()))) {
            try { engine.getObject().replay(command.workflowId(), command.stepName()); }
            catch (Exception error) { operation.error(error); throw error; }
        }
    }
}
