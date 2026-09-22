package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

/**
 * Immutable outcome of a step execution: the resulting {@link Status}, the (possibly
 * mutated) {@link WorkflowContext} to persist and propagate to the next step, and an
 * optional human-readable message (surfaced in the audit trail and dashboard).
 *
 * @param status  terminal or in-flight status of the step
 * @param context context to persist/propagate after this step
 * @param message optional message describing the outcome, useful for troubleshooting
 *                and shown verbatim in log lines and the dashboard audit log
 */
public record
StepResult(Status status, WorkflowContext context, String message) {
    /**
     * Creates a {@link Status#SUCCESS} result.
     */
    public static StepResult success(WorkflowContext c) {
        return new StepResult(Status.SUCCESS, c, null);
    }

    /**
     * Creates a {@link Status#RUNNING} result.
     */
    public static StepResult running(WorkflowContext c) {
        return new StepResult(Status.RUNNING, c, null);
    }

    /**
     * Creates a {@link Status#FAILED} result with the given failure message.
     */
    public static StepResult failed(WorkflowContext c, String m) {
        return new StepResult(Status.FAILED, c, m);
    }

    /**
     * Creates a {@link Status#RUNNING_ASYNC} result.
     */
    public static StepResult runningAsync(WorkflowContext context) {
        return new StepResult(Status.RUNNING_ASYNC, context, null);
    }
}
