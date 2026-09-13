package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;

public class TypeAsyncWorkflowStep extends AsyncStep {
    private final Object target;
    private final String name;

    public TypeAsyncWorkflowStep(Object target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public StepResult executeAsync(PipelineContext context) throws Exception {
        Object result = null;
        if (target instanceof AsyncStep step) {
            result = step.executeAsync(context);
            return result(context, result);
        }
        throw new IllegalStateException("Target is not an AsyncStep instance");
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
