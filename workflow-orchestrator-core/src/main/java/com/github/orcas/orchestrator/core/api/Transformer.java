package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Objects;
import java.util.function.Supplier;

/** Transforms the current business input into a new business value. */
public abstract class Transformer<I, O> extends Step {
    @SuppressWarnings("unchecked")
    protected I input(WorkflowContext context) {
        return (I) context.businessInput();
    }

    protected abstract O transform(I input) throws Exception;

    @Override
    public StepResult execute(WorkflowContext context) throws Exception {
        return StepResult.success(context.withBusinessInput(transform(input(context))));
    }

    public O transformFrom(Supplier<I> supplier) throws Exception {
        return transform(Objects.requireNonNull(supplier, "supplier").get());
    }
}
