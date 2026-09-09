package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;
import java.util.function.Function;

/** One-line functional workflow step. */
public final class FunctionalStep extends Step {
    private final String name;
    private final Function<PipelineContext, Object> function;

    public FunctionalStep(String name, Function<PipelineContext, Object> function) {
        this.name = name; this.function = function;
    }
    @Override public String name() { return name; }
    @Override public StepResult execute(PipelineContext context) {
        Object value = function.apply(context);
        return value instanceof StepResult r ? r :
                value instanceof PipelineContext c ? StepResult.success(c) :
                StepResult.success(context.withBusinessInput(value));
    }
}
