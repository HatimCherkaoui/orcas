package com.github.orcas.orchestrator.core.annotation;

import java.lang.annotation.*;

/**
 * Declares a class as the definition of a workflow. Classes annotated with
 * {@code @Workflow} are typically scanned at startup and their routing table built
 * with {@code com.github.orcas.orchestrator.core.builder.PipelineBuilder}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Workflow {
    /** Unique workflow name, used to look it up in the {@code WorkflowRegistry}. */
    String value();
}
