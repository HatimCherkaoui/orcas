package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.error.ErrorDisposition;
import com.github.orcas.orchestrator.core.error.WorkflowError;

/** Signals that a failed step can be replayed by the configured retry policy. */
@SuppressWarnings("serial")
public class WorkflowRetryableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String step;
    private final WorkflowError error;

    /** Creates a replayable error with a textual reason. */
    public WorkflowRetryableException(String step, String message) {
        this(step, new WorkflowError(ErrorDisposition.REPLAYABLE, message, null));
    }

    /** Creates a replayable error with an underlying cause. */
    public WorkflowRetryableException(String step, String message, Throwable cause) {
        this(step, new WorkflowError(ErrorDisposition.REPLAYABLE, message, cause));
    }

    /** Creates an exception from a classified workflow error. */
    public WorkflowRetryableException(String step, WorkflowError error) {
        super(error.reason(), error.cause());
        this.step = step;
        this.error = error;
    }

    /** Returns the failed step name. */
    public String step() {
        return step;
    }

    /** Returns the classified workflow error. */
    public WorkflowError error() {
        return error;
    }

    /** Returns whether the failure may be replayed. */
    public boolean replayable() {
        return error.replayable();
    }
}
