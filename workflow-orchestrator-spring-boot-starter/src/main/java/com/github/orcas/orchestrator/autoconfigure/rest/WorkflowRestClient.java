package com.github.orcas.orchestrator.autoconfigure.rest;

import java.lang.annotation.*;

/**
 * Marks a Java interface as a declarative REST client for a workflow step. The
 * starter generates a dynamic proxy (backed by a reactive {@code WebClient}) that
 * implements the interface; methods annotated with
 * {@code @com.github.orcas.orchestrator.core.annotation.WorkflowStep} are exposed as
 * {@link RestClientWorkflowStep}/{@link AsyncRestClientWorkflowStep} instances
 * registered in the step catalog.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowRestClient {
    /** Base URL of the remote service, supports {@code ${...}} property placeholders. */
    String baseUrl();
}
