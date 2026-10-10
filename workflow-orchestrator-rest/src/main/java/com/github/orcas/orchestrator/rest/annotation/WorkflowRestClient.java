package com.github.orcas.orchestrator.rest.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java interface as a declarative REST client used by workflow steps.
 * The REST starter creates the implementation proxy.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowRestClient {
    /** Base URL of the remote service; Spring placeholders are supported. */
    String baseUrl();
}
