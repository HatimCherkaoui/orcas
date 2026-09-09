package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.error.ErrorDisposition;
import com.github.orcas.orchestrator.core.error.WorkflowError;

@SuppressWarnings("serial")
public class WorkflowRetryableException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String step;
    private final WorkflowError error;
    public WorkflowRetryableException(String step, String message) {
        this(step, new WorkflowError(ErrorDisposition.REPLAYABLE, message, null));
    }
    public WorkflowRetryableException(String step, String message, Throwable cause) {
        this(step, new WorkflowError(ErrorDisposition.REPLAYABLE, message, cause));
    }
    public WorkflowRetryableException(String step, WorkflowError error) {
        super(error.reason(), error.cause());
        this.step = step;
        this.error = error;
    }
    public String step() { return step; }
    public WorkflowError error() { return error; }
    public boolean replayable() { return error.replayable(); }
}
