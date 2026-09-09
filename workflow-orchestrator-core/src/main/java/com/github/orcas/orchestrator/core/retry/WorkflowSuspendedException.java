package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.error.WorkflowError;

@SuppressWarnings("serial")
public final class WorkflowSuspendedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String step;
    private final WorkflowError error;

    public WorkflowSuspendedException(String step, WorkflowError error) {
        super("Workflow suspended at step '" + step + "': " + error.reason(), error.cause());
        this.step = step;
        this.error = error;
    }

    public String step() { return step; }
    public WorkflowError error() { return error; }
}
