package com.github.orcas.orchestrator.core.engine;

import java.util.List;

/** Optional persistence capability used by retry/replay integrations. */
public interface WorkflowRetryStateStore {
    List<String> suspendedWorkflowIds(String stepName);

    /** Number of automatic retries already scheduled for a step; zero for new steps. */
    default int retryCount(String workflowId, String stepName) { return 0; }

    void recordRetry(String workflowId, String stepName, int attempt, String reason);
}
