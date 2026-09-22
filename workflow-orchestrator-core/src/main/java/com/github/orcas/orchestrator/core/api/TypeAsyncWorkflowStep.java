package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

public class TypeAsyncWorkflowStep extends AsyncStep {
    private final Object target;
    private final String name;

    public TypeAsyncWorkflowStep(Object target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public StepResult executeAsync(WorkflowContext context) throws Exception {
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

    private StepResult result(WorkflowContext context, Object value) {
        if (value == null) return StepResult.success(context);
        if (value instanceof StepResult stepResult) return stepResult;
        if (value instanceof WorkflowContext workflowContext) return StepResult.success(workflowContext);
        return StepResult.success(context.withBusinessInput(value));
    }
}
