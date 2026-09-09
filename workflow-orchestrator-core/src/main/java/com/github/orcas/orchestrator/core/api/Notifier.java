package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;

@SuppressWarnings("unchecked")
public abstract class Notifier<I> extends AsyncStep {
    protected I input(PipelineContext context) {
        return (I) context.businessInput();
    }

    protected abstract void notify(I input);

    @Override
    public StepResult executeAsync(PipelineContext context) {
        notify(input(context));
        return StepResult.success(context);
    }
}
