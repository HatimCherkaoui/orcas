package com.github.orcas.orchestrator.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class or method as a workflow step. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowStep {
    /** Optional step name. The class or method name is used when empty. */
    String value() default "";

    /** Runs this step on the workflow async executor. */
    boolean async() default false;
}
