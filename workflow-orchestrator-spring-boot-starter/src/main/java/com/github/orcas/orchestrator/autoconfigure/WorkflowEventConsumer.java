package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka listener that feeds every {@link StatusEvent} published on the orchestrator
 * topic back into the {@link WorkflowEngine}, closing the publish/consume loop that
 * drives workflow progression.
 */
public final class WorkflowEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEventConsumer.class);

    private final WorkflowEngine engine;
    private final ObjectMapper mapper;

    public WorkflowEventConsumer(WorkflowEngine e, ObjectMapper m) {
        engine = e;
        mapper = m;
    }

    @KafkaListener(topics = "${workflow.orchestrator.topic}", groupId = "${workflow.orchestrator.consumer-group}", containerFactory = "workflowKafkaListenerContainerFactory")
    public void onMessage(String message) throws Exception {
        StatusEvent event = mapper.readValue(message, StatusEvent.class);
        log.debug("Consumed status event workflowId={} step='{}' status={}", event.workflowId(), event.step(), event.status());
        engine.handle(event);
    }
}
