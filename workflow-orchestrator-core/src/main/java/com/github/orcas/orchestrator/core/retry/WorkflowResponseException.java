package com.github.orcas.orchestrator.core.retry;

import com.github.orcas.orchestrator.core.error.WorkflowError;

/** Neutral exception used by adapters when a remote response is not acceptable. */
public final class WorkflowResponseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final transient WorkflowError error;

    public WorkflowResponseException(String step, int statusCode, WorkflowError error) {
        super("Workflow step '" + step + "' received response status " + statusCode);
        this.statusCode = statusCode;
        this.error = error;
    }

    public int statusCode() { return statusCode; }
    public WorkflowError error() { return error; }
}
