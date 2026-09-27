package com.github.orcas.orchestrator.core.error;

/** Engine-level action selected after a step failure. */
public enum ErrorDisposition {
    REPLAYABLE,
    SUSPEND
}
