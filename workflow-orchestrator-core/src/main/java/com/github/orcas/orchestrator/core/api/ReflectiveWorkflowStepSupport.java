package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Metadata;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ReflectiveWorkflowStepSupport {
    private ReflectiveWorkflowStepSupport() {
    }

    static Object invoke(Object target, Method method, WorkflowContext context) throws Exception {
        try {
            return method.invoke(target, arguments(method, context));
        } catch (InvocationTargetException exception) {
            throw propagate(exception, method);
        }
    }

    static Object resolveCompletionStage(Object value) {
        if (value instanceof java.util.concurrent.CompletionStage<?> stage) {
            return stage.toCompletableFuture().join();
        }
        return value;
    }

    static Object[] arguments(Method method, WorkflowContext context) {
        return switch (method.getParameterCount()) {
            case 0 -> new Object[0];
            case 1 -> new Object[]{argument(method.getParameterTypes()[0], context)};
            default -> throw new IllegalArgumentException(
                    "Workflow step method must have zero or one parameter: " + method);
        };
    }

    private static Object argument(Class<?> type, WorkflowContext context) {
        if (WorkflowContext.class.isAssignableFrom(type)) {
            return context;
        }
        if (Metadata.class.isAssignableFrom(type)) {
            return context.metadata();
        }
        if (StepExecutionContext.class.isAssignableFrom(type)) {
            return WorkflowContextHolder.step();
        }
        return context.businessInput();
    }

    private static Exception propagate(InvocationTargetException exception, Method method) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        if (cause instanceof Exception handled) {
            return handled;
        }
        return new IllegalStateException("Workflow step failed: " + method, cause);
    }
}
