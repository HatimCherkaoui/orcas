package com.github.orcas.orchestrator.resilience.annotation;

/** Action to take when a protected step cannot continue. */
public enum FallbackStrategy {
    SUSPEND,
    REPLAY
}
