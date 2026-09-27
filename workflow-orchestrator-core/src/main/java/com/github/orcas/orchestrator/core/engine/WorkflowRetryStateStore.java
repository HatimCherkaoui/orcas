package com.github.orcas.orchestrator.core.engine;

import java.util.List;

/** Optional persistence capability used by retry/replay integrations. */
public interface WorkflowRetryStateStore {
    List<String> suspendedWorkflowIds(String stepName);

    void recordRetry(String workflowId, String stepName, int attempt, String reason);
}
