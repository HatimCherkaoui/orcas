package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Metadata;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

/** Adapter that turns an annotated application method into a workflow step. */
public final class MethodWorkflowStep extends Step {
    private final Object target;
    private final Method method;
    private final String name;

    public MethodWorkflowStep(Object target, Method method, String name) {
        this.target = target;
        this.method = method;
        this.name = name;
        method.trySetAccessible();
    }

    @Override public String name() { return name; }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        Object result = method.invoke(target, arguments(context));
        if (result instanceof CompletionStage<?> stage) {
            Object value = stage.toCompletableFuture().join();
            return result(context, value);
        }
        return result(context, result);
    }

    private Object[] arguments(PipelineContext context) {
        var parameters = method.getParameterTypes();
        if (parameters.length == 0) return new Object[0];
        if (parameters.length == 1) {
            Class<?> type = parameters[0];
            if (PipelineContext.class.isAssignableFrom(type)) return new Object[]{context};
            if (Metadata.class.isAssignableFrom(type)) return new Object[]{context.metadata()};
            if (StepExecutionContext.class.isAssignableFrom(type)) {
                var execution = com.github.orcas.orchestrator.core.model.WorkflowContextHolder.step();
                return new Object[]{execution};
            }
            return new Object[]{context.businessInput()};
        }
        throw new IllegalArgumentException("Workflow step method must have zero or one parameter: " + method);
    }

    private StepResult result(PipelineContext context, Object value) {
        if (value == null) return StepResult.success(context);
        if (value instanceof StepResult stepResult) return stepResult;
        if (value instanceof PipelineContext pipelineContext) return StepResult.success(pipelineContext);
        return StepResult.success(context.withBusinessInput(value));
    }
}
