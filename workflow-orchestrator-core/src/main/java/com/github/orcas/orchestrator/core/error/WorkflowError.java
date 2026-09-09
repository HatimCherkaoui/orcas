package com.github.orcas.orchestrator.core.error;

public record WorkflowError(ErrorDisposition disposition, String reason, Throwable cause) {
    public boolean replayable() { return disposition == ErrorDisposition.REPLAYABLE; }
}
