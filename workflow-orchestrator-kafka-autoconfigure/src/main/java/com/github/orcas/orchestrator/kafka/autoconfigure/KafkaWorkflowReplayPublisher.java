package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.model.WorkflowReplayCommand;
import com.github.orcas.orchestrator.kafka.autoconfigure.WorkflowKafkaProperties;
import com.github.orcas.orchestrator.service.api.WorkflowReplayPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

/** Publishes operator replay requests to the workflow runtime over Kafka. */
public final class KafkaWorkflowReplayPublisher implements WorkflowReplayPublisher {
    private final KafkaTemplate<String, String> template;
    private final ObjectMapper mapper;
    private final WorkflowKafkaProperties properties;

    public KafkaWorkflowReplayPublisher(KafkaTemplate<String, String> template,
                                        ObjectMapper mapper,
                                        WorkflowKafkaProperties properties) {
        this.template = template;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public void publish(String workflowId, String stepName) {
        try {
            var command = new WorkflowReplayCommand(workflowId, stepName);
            template.send(properties.getReplayTopic(), workflowId, mapper.writeValueAsString(command));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to publish workflow replay command", e);
        }
    }
}
