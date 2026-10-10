package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.function.Function;

/** Functional factories for concise, composable workflow steps. */
public final class Steps {
    private Steps() {
    }

    public static FunctionalStep step(String name, Function<WorkflowContext, ?> action) {
        return new FunctionalStep(name, action);
    }

    public static FunctionalStep step(Function<WorkflowContext, ?> action) {
        return new FunctionalStep(action);
    }

}
