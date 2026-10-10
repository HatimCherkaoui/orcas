package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

/**
 * Normalizes common functional-step return values into {@link StepResult}.
 */
public final class StepResults {
    private StepResults() {
    }

    public static StepResult from(WorkflowContext context, Object value) {
        return switch (value) {
            case null -> StepResult.success(context);
            case StepResult result -> result;
            case WorkflowContext next -> StepResult.success(next);
            default -> StepResult.success(context.withBusinessInput(value));
        };
    }

    public static StepResult status(Status status, WorkflowContext context, String message) {
        return new StepResult(status, context, message);
    }
}
