package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Objects;
import java.util.function.Supplier;

/** Reads an input value from workflow state and converts it into another value. */
public abstract class Extractor<I, O> extends Step {
    protected abstract I input(WorkflowContext context);

    protected abstract O extract(I input) throws Exception;

    @Override
    public StepResult execute(WorkflowContext context) throws Exception {
        return StepResult.success(context.withBusinessInput(extract(input(context))));
    }

    public O extractFrom(Supplier<I> supplier) throws Exception {
        return extract(Objects.requireNonNull(supplier, "supplier").get());
    }
}
