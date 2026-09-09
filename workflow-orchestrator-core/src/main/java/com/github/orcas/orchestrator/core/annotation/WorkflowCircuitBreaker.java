package com.github.orcas.orchestrator.core.annotation;

import java.lang.annotation.*;
import java.time.Duration;

/**
 * Wraps a workflow step method (or all step methods of a class) with a
 * <a href="https://resilience4j.readme.io/docs/circuitbreaker">Resilience4j</a>
 * circuit breaker, applied by {@code WorkflowCircuitBreakerAspect}.
 *
 * <p>When the breaker trips to {@code OPEN} and {@link #fallback()} is
 * {@code "REPLAY"}, the step is scheduled for an automatic replay after
 * {@link #retryDelaySeconds()}; otherwise the workflow instance is suspended and
 * requires a manual replay.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowCircuitBreaker {
    /** Name of the Resilience4j {@code CircuitBreaker} instance to use/create. */
    String name() default "default";

    /** Either {@code "REPLAY"} (auto-schedule a replay) or {@code "SUSPEND"} (default). */
    String fallback() default "SUSPEND";

    /** Delay, in seconds, before an automatic replay is attempted when {@code fallback = "REPLAY"}. */
    long retryDelaySeconds() default 30;
}
