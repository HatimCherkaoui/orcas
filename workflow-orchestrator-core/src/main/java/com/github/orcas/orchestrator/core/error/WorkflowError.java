package com.github.orcas.orchestrator.core.error;

import java.time.Duration;

/** Classified workflow failure with stable diagnostic fields and an optional originating exception. */
public record WorkflowError(
        ErrorDisposition disposition,
        String category,
        String code,
        String reason,
        Throwable cause,
        Duration retryAfter) {

    /** Compatibility constructor for callers that supply only a reason and cause. */
    public WorkflowError(ErrorDisposition disposition, String reason, Throwable cause) {
        this(disposition, "APPLICATION", null, reason, cause, null);
    }

    /** Compatibility constructor for callers without a Retry-After hint. */
    public WorkflowError(ErrorDisposition disposition, String category, String code,
                         String reason, Throwable cause) {
        this(disposition, category, code, reason, cause, null);
    }

    public boolean replayable() {
        return disposition == ErrorDisposition.REPLAYABLE;
    }

    public WorkflowError withDisposition(ErrorDisposition newDisposition) {
        return new WorkflowError(newDisposition, category, code, reason, cause, retryAfter);
    }
}
