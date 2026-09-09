package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;

@FunctionalInterface
public interface ContextMapper<I> {
    I map(StepExecutionContext context) throws Exception;

    final class Identity implements ContextMapper<Object> {
        @Override public Object map(StepExecutionContext context) { return context.input(); }
    }
}
