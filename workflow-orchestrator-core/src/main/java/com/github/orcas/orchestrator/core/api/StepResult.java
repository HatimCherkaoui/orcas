package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Objects;

/** Immutable outcome of a workflow step execution. */
public record StepResult(Status status, WorkflowContext context, String message) {

    public StepResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(context, "context");
    }

    /** Creates a successful result. */
    public static StepResult success(WorkflowContext context) {
        return new StepResult(Status.SUCCESS, context, null);
    }

    /** Creates a running result. */
    public static StepResult running(WorkflowContext context) {
        return new StepResult(Status.RUNNING, context, null);
    }

    /** Creates a failed result with a message. */
    public static StepResult failed(WorkflowContext context, String message) {
        return new StepResult(Status.FAILED, context, message);
    }

    /** Creates a result for an asynchronously running step. */
    public static StepResult runningAsync(WorkflowContext context) {
        return new StepResult(Status.RUNNING_ASYNC, context, null);
    }
}
