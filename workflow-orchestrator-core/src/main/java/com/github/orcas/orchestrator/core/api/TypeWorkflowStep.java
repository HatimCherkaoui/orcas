package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;

import java.lang.reflect.Method;
import java.lang.runtime.ObjectMethods;
import java.util.concurrent.CompletionStage;

public class TypeWorkflowStep extends Step {
    private final Object target;
    private final String name;
    public TypeWorkflowStep(Object target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        Object result = null;
        if (target instanceof Step step) {
            result = step.execute(context);
            return result(context, result);
        }
        throw new IllegalStateException("Target is not a Step instance");
    }

    @Override
    public String name() {
        return name;
    }

    private StepResult result(PipelineContext context, Object value) {
        if (value == null) return StepResult.success(context);
        if (value instanceof StepResult stepResult) return stepResult;
        if (value instanceof PipelineContext pipelineContext) return StepResult.success(pipelineContext);
        return StepResult.success(context.withBusinessInput(value));
    }
}
