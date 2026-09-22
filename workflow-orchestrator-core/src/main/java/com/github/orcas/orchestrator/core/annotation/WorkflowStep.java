package com.github.orcas.orchestrator.core.annotation;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;

import java.lang.annotation.*;

/**
 * Marks a method (or a class implementing
 * {@code com.github.orcas.orchestrator.core.api.WorkflowStep}) as a workflow step, scanned by
 * {@code WorkflowMethodStepScanner}/{@code WorkflowClassRegistrar} and registered into
 * the {@code StepCatalog} under {@link #value()}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowStep {
    /**
     * Unique step name used for routing, replay and log/audit correlation.
     */
    String value();

    /**
     * Whether this step should be executed asynchronously (see {@code AsyncStep}).
     */
    boolean async() default false;

    /**
     * Optional mapper used by infrastructure-backed steps such as REST clients.
     * Regular application methods can ignore it.
     */
    Class<? extends ContextMapper<?>> mapper() default ContextMapper.Identity.class;


    Class<? extends ResponseConsumer<?>> responseSubscriber() default ResponseConsumer.Void.class;
}
