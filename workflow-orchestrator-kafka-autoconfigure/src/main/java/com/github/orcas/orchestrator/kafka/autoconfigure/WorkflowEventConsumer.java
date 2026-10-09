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
    public void onMessage(String message) throws Exception {
        StatusEvent event = mapper.readValue(message, StatusEvent.class);
        engine.getObject().handle(event);
    }
}
