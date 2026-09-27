package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.lang.reflect.Method;
import java.util.Objects;
/** Adapts an annotated application method into an asynchronous workflow step. */
public final class MethodAsyncWorkflowStep extends AsyncStep {
    private final Object target;
    private final Method method;
    private final String name;

    public MethodAsyncWorkflowStep(Object target, Method method) {
        this.target = Objects.requireNonNull(target, "target");
        this.method = Objects.requireNonNull(method, "method");
        this.name = StepNames.of(method);
        method.trySetAccessible();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public StepResult executeAsync(WorkflowContext context) throws Exception {
        Object value = ReflectiveWorkflowStepSupport.invoke(target, method, context);
        return StepResults.from(context, ReflectiveWorkflowStepSupport.resolveCompletionStage(value));
    }
}
