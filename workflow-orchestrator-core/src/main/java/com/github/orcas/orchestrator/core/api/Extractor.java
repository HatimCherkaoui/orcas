package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.function.Supplier;

public abstract class Extractor<I, O> extends Step {
    protected abstract I input(WorkflowContext c);

    protected abstract O extract(I input) throws Exception;

    @Override
    public StepResult execute(WorkflowContext c) throws Exception {
        return StepResult.success(c.withBusinessInput(extract(input(c))));
    }

    public O extractFrom(Supplier<I> s) throws Exception {
        return extract(s.get());
    }
}
