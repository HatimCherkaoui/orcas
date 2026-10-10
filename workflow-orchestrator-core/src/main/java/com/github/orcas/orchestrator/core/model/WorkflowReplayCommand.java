package com.github.orcas.orchestrator.core.model;

/** Command sent to a workflow runtime to replay a persisted step. */
public record WorkflowReplayCommand(String workflowId, String stepName, java.util.Map<String, String> metadata) {
    public WorkflowReplayCommand(String workflowId, String stepName) {
        this(workflowId, stepName, java.util.Map.of());
    }
    public WorkflowReplayCommand {
        metadata = CorrelationIdentifiers.ensure(new Metadata(metadata)).asMap();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("stepName must not be blank");
        }
    }
}
