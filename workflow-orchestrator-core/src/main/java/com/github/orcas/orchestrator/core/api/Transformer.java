package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;

import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public abstract class Transformer<I, O> extends Step {
    protected I input(PipelineContext c) {
        return (I) c.businessInput();
    }

    protected abstract O transform(I input) throws Exception;

    @Override
    public StepResult execute(PipelineContext c) throws Exception {
        return StepResult.success(c.withBusinessInput(transform(input(c))));
    }

    public O transformFrom(Supplier<I> s) throws Exception {
        return transform(s.get());
    }
}
