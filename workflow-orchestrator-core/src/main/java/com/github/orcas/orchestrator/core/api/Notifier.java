package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

/** Performs a fire-and-forget action using the current business input. */
public abstract class Notifier<I> extends AsyncStep {
    @SuppressWarnings("unchecked")
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
