package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.error.WorkflowError;

/** Stops a step and records it as suspended instead of retrying immediately. */
@SuppressWarnings("serial")
public final class WorkflowSuspendedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String step;
    private final WorkflowError error;

    /** Creates a suspension for a classified workflow failure. */
    public WorkflowSuspendedException(String step, WorkflowError error) {
        super("Workflow suspended at step '" + step + "': " + error.reason(), error.cause());
        this.step = step;
        this.error = error;
    }

    /** Returns the suspended step name. */
    public String step() {
        return step;
    }

    /** Returns the classified suspension reason. */
    public WorkflowError error() {
        return error;
    }
}
