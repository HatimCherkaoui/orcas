package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

public class TypeWorkflowStep extends Step {
    private final Object target;
    private final String name;

    public TypeWorkflowStep(Object target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public StepResult execute(WorkflowContext context) throws Exception {
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

    private StepResult result(WorkflowContext context, Object value) {
        if (value == null) return StepResult.success(context);
        if (value instanceof StepResult stepResult) return stepResult;
        if (value instanceof WorkflowContext workflowContext) return StepResult.success(workflowContext);
        return StepResult.success(context.withBusinessInput(value));
    }
}
