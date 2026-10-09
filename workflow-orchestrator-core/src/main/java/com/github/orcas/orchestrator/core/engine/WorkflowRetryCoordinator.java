package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.error.WorkflowError;

/** Optional runtime policy for bounded automatic replay of transient step failures. */
public interface WorkflowRetryCoordinator {
    default boolean automaticRetriesEnabled() { return true; }

    boolean retryAllowed(String workflowId, String stepName);

    void scheduleRetry(String workflowId, String stepName, WorkflowError error);
}
