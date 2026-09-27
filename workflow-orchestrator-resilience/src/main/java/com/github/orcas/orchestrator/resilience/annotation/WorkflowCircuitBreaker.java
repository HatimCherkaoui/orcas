package com.github.orcas.orchestrator.resilience.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applies a Resilience4j circuit breaker to a workflow step invocation. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowCircuitBreaker {
    String name() default "";
    FallbackStrategy fallback() default FallbackStrategy.SUSPEND;
}
