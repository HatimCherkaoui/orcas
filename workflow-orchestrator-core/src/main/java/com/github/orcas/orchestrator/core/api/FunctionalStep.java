package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Objects;
import java.util.function.Function;

/** Adapts a function into a synchronous workflow step. */
public final class FunctionalStep extends Step {
    private final String name;
    private final Function<WorkflowContext, ?> action;

    public FunctionalStep(String name, Function<WorkflowContext, ?> action) {
        this.name = Objects.requireNonNull(name, "name");
        this.action = Objects.requireNonNull(action, "action");
    }

    public FunctionalStep(Function<WorkflowContext, ?> action) {
        this("lambdaStep", action);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public StepResult execute(WorkflowContext context) {
        return StepResults.from(context, action.apply(context));
    }
}
