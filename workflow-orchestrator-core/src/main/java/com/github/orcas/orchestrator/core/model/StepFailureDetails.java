package com.github.orcas.orchestrator.core.model;

import com.github.orcas.orchestrator.core.error.WorkflowError;

/** Safe, serializable diagnostics for a failed or suspended workflow step. */
public record StepFailureDetails(
        String disposition,
        String category,
        String code,
        String exceptionType,
        String message) {

    public static StepFailureDetails from(WorkflowError error) {
        Throwable cause = error.cause();
        return new StepFailureDetails(
                error.disposition().name(),
                error.category(),
                error.code(),
                cause == null ? (error.code() != null && error.code().startsWith("HTTP ") ? "HTTP response" : null)
                        : cause.getClass().getName(),
                error.reason());
    }
}
