package com.github.orcas.orchestrator.core.model;

/** Lifecycle states emitted by the workflow engine. */
public enum Status {
    INIT,
    STARTED,
    RUNNING,
    RUNNING_ASYNC,
    SUCCESS,
    FAILED,
    SUSPENDED,
    ABANDONED,
    SKIPPED
}
