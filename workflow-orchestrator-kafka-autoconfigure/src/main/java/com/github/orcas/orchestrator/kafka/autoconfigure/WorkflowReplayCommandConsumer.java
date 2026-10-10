package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.WorkflowReplayCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.logging.Logger;

/** Receives operator replay commands in the workflow runtime application. */
public final class WorkflowReplayCommandConsumer {
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
    public void onMessage(String message) throws Exception {
        var command = mapper.readValue(message, WorkflowReplayCommand.class);
        log.info("Replaying workflowId='" + command.workflowId() + "' step='" + command.stepName() + "'");
        engine.getObject().replay(command.workflowId(), command.stepName());
    }
}
