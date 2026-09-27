package com.github.orcas.orchestrator.core.api;

import java.lang.reflect.Method;

/**
 * Optional interception point for integrations such as circuit breakers.
 * Core never depends on the integration implementation.
 */
@FunctionalInterface
public interface WorkflowStepInvocationInterceptor {
    Object invoke(Class<?> owner, Method method, Invocation invocation) throws Throwable;

    @FunctionalInterface
    interface Invocation {
        Object proceed() throws Throwable;
    }
}
