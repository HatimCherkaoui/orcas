package com.github.orcas.orchestrator.core.error;

/** Classified workflow failure with an optional originating exception. */
public record WorkflowError(ErrorDisposition disposition, String reason, Throwable cause) {
    public boolean replayable() {
        return disposition == ErrorDisposition.REPLAYABLE;
    }
}
