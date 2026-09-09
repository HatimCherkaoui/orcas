package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.model.StatusEvent;

public final class WorkflowCriteriaNotMatchedException extends WorkflowRetryableException {
    private static final long serialVersionUID = 1L;

    public WorkflowCriteriaNotMatchedException(StatusEvent e) {
        super(e.step(), "Criteria not matched for " + e.workflow() + "/" + e.workflowId() + "/" + e.step() + "/" + e.status());
    }
}
