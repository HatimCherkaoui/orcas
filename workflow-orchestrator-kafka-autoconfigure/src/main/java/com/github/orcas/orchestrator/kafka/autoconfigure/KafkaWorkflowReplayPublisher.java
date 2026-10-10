package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.model.WorkflowReplayCommand;
import com.github.orcas.orchestrator.kafka.autoconfigure.WorkflowKafkaProperties;
import com.github.orcas.orchestrator.service.api.WorkflowReplayPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

/** Publishes operator replay requests to the workflow runtime over Kafka. */
public final class KafkaWorkflowReplayPublisher implements WorkflowReplayPublisher {
    private com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry = com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.noop();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTelemetry(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { this.telemetry = telemetry; }
    private org.springframework.beans.factory.ObjectProvider<com.github.orcas.orchestrator.service.api.WorkflowQueryService> queries;
    @org.springframework.beans.factory.annotation.Autowired
    public void setQueries(org.springframework.beans.factory.ObjectProvider<com.github.orcas.orchestrator.service.api.WorkflowQueryService> queries) { this.queries = queries; }
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
        var current = com.github.orcas.orchestrator.core.model.WorkflowContextHolder.current();
        var metadata = current == null ? com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.fromHeaders(java.util.Map.of()) : current.metadata();
        var query = queries == null ? null : queries.getIfAvailable();
        if (query != null) {
            var persisted = query.metadata(workflowId);
            if (persisted.isPresent()) metadata = new com.github.orcas.orchestrator.core.model.Metadata(persisted.get().values());
        }
        var operation = telemetry.begin("Dashboard", "workflow.replay.request", metadata, java.util.Map.of("workflowId", workflowId,"workflowStep",stepName));
        try {
            var command = new WorkflowReplayCommand(workflowId, stepName, metadata.identifiers());
            var headers = operation.propagation();
            if (headers.isEmpty()) headers = com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.headers(metadata);
            var send = template.send(KafkaCorrelation.record(properties.getReplayTopic(), workflowId, mapper.writeValueAsString(command), headers));
            operation.detach();
            send.whenComplete((result,error) -> { if (error != null) operation.error(error); operation.close(); });
        } catch (Exception e) {
            operation.error(e); operation.close();
            throw new IllegalStateException("Unable to publish workflow replay command", e);
        }
    }
}
