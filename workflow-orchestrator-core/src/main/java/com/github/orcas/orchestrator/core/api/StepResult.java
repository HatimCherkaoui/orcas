package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.Status;

/**
 * Immutable outcome of a step execution: the resulting {@link Status}, the (possibly
 * mutated) {@link PipelineContext} to persist and propagate to the next step, and an
 * optional human-readable message (surfaced in the audit trail and dashboard).
 *
 * @param status  terminal or in-flight status of the step
 * @param context context to persist/propagate after this step
 * @param message optional message describing the outcome, useful for troubleshooting
 *                and shown verbatim in log lines and the dashboard audit log
 */
public record
StepResult(Status status, PipelineContext context, String message) {
    /** Creates a {@link Status#SUCCESS} result. */
    public static StepResult success(PipelineContext c) {
        return new StepResult(Status.SUCCESS, c, null);
    }

    /** Creates a {@link Status#RUNNING} result. */
    public static StepResult running(PipelineContext c) {
        return new StepResult(Status.RUNNING, c, null);
    }

    /** Creates a {@link Status#FAILED} result with the given failure message. */
    public static StepResult failed(PipelineContext c, String m) {
        return new StepResult(Status.FAILED, c, m);
    }

    /** Creates a {@link Status#RUNNING_ASYNC} result. */
    public static StepResult runningAsync(PipelineContext context) {
        return new StepResult(Status.RUNNING_ASYNC, context, null);
    }
}
