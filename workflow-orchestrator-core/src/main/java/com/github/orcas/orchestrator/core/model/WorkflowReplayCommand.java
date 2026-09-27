package com.github.orcas.orchestrator.core.model;

/** Command sent to a workflow runtime to replay a persisted step. */
public record WorkflowReplayCommand(String workflowId, String stepName) {
    public WorkflowReplayCommand {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("stepName must not be blank");
        }
    }
}
