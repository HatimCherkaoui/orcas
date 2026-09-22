package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

@SuppressWarnings("unchecked")
public abstract class Notifier<I> extends AsyncStep {
    protected I input(WorkflowContext context) {
        return (I) context.businessInput();
    }

    protected abstract void notify(I input);

    @Override
    public StepResult executeAsync(WorkflowContext context) {
        notify(input(context));
        return StepResult.success(context);
    }
}
