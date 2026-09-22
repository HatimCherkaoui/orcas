package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public abstract class Loader<I, O> extends Step {
    protected I input(WorkflowContext c) {
        return (I) c.businessInput();
    }

    protected abstract O load(I input) throws Exception;

    @Override
    public StepResult execute(WorkflowContext c) throws Exception {
        return StepResult.success(c.withBusinessInput(load(input(c))));
    }

    public O loadFrom(Supplier<I> s) throws Exception {
        return load(s.get());
    }
}
