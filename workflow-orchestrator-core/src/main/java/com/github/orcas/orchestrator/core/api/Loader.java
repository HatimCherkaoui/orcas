package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Objects;
import java.util.function.Supplier;

/** Loads a value using the current business input as the loader argument. */
public abstract class Loader<I, O> extends Step {
    @SuppressWarnings("unchecked")
    protected I input(WorkflowContext context) {
        return (I) context.businessInput();
    }

    protected abstract O load(I input) throws Exception;

    @Override
    public StepResult execute(WorkflowContext context) throws Exception {
        return StepResult.success(context.withBusinessInput(load(input(context))));
    }

    public O loadFrom(Supplier<I> supplier) throws Exception {
        return load(Objects.requireNonNull(supplier, "supplier").get());
    }
}
