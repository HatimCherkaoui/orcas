package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Metadata;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

public final class MethodAsyncWorkflowStep extends AsyncStep {

    private final Object target;
    private final Method method;
    private final String name;

    public MethodAsyncWorkflowStep(Object target, Method method, String name) {
        this.target = target;
        this.method = method;
        this.name = name;
        method.trySetAccessible();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public StepResult executeAsync(PipelineContext context) throws Exception {
        Object result = method.invoke(target, arguments(context));
        if (result instanceof CompletionStage<?> stage) result = stage.toCompletableFuture().join();
        if (result instanceof StepResult sr) return sr;
        if (result instanceof PipelineContext pc) return StepResult.success(pc);
        return result == null ? StepResult.success(context) : StepResult.success(context.withBusinessInput(result));
    }

    private Object[] arguments(PipelineContext context) {
        var p = method.getParameterTypes();
        if (p.length == 0) return new Object[0];
        if (p.length == 1) {
            if (PipelineContext.class.isAssignableFrom(p[0])) return new Object[]{context};
            if (Metadata.class.isAssignableFrom(p[0])) return new Object[]{context.metadata()};
            if (StepExecutionContext.class.isAssignableFrom(p[0])) return new Object[]{com.github.orcas.orchestrator.core.model.WorkflowContextHolder.step()};
            return new Object[]{context.businessInput()};
        }
        throw new IllegalArgumentException("Workflow step method must have zero or one parameter: " + method);
    }
}
