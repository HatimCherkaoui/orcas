package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.error.WorkflowError;

@SuppressWarnings("serial")
public final class WorkflowResponseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String step;
    private final int statusCode;
    private final WorkflowError error;
    public WorkflowResponseException(String step, int statusCode, WorkflowError error) {
        super("HTTP " + statusCode + " at step '" + step + "': " + error.reason(), error.cause());
        this.step = step; this.statusCode = statusCode; this.error = error;
    }
    public String step() { return step; }
    public int statusCode() { return statusCode; }
    public WorkflowError error() { return error; }
}
