package com.github.orcas.orchestrator.service.api;

/** Transport abstraction used by the management service to request a replay from a runtime. */
@FunctionalInterface
public interface WorkflowReplayPublisher {
    void publish(String workflowId, String stepName);
}
