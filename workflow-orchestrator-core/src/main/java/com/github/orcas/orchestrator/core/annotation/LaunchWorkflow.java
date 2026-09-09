package com.github.orcas.orchestrator.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a Spring MVC (or similar) controller method as the entry point that starts a
 * new workflow instance. Applied by {@code WorkflowLaunchAspect}, which intercepts the
 * annotated method, builds a {@code PipelineContext} from the method's request
 * body/headers and starts the named workflow before the controller method body runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LaunchWorkflow {
    /** Name of the workflow to start, matching {@link Workflow#value()}. */
    String value();
}
