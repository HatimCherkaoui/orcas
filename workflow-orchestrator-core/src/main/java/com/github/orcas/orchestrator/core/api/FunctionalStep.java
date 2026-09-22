package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.function.Function;

/**
 * One-line functional workflow step.
 */
public final class FunctionalStep extends Step {
    private final String name;
    private final Function<WorkflowContext, Object> function;

    public FunctionalStep(String name, Function<WorkflowContext, Object> function) {
        this.name = name;
        this.function = function;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public StepResult execute(WorkflowContext context) {
        Object value = function.apply(context);
        return value instanceof StepResult r ? r :
                value instanceof WorkflowContext c ? StepResult.success(c) :
                        StepResult.success(context.withBusinessInput(value));
    }
}
