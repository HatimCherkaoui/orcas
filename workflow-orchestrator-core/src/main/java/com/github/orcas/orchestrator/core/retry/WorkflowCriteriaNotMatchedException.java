package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.model.StatusEvent;

/** Indicates that a terminal event did not satisfy a waiting route. */
public final class WorkflowCriteriaNotMatchedException extends WorkflowRetryableException {
    private static final long serialVersionUID = 1L;

    /** Creates an exception describing the unmatched workflow event. */
    public WorkflowCriteriaNotMatchedException(StatusEvent event) {
        super(
                event.step(),
                "Criteria not matched for "
                        + event.workflow()
                        + "/"
                        + event.workflowId()
                        + "/"
                        + event.step()
                        + "/"
                        + event.status());
    }
}
