package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;

/** Maps workflow execution state into an input expected by an adapter. */
@FunctionalInterface
public interface ContextMapper<I> {
    I map(StepExecutionContext context) throws Exception;

    /** Default mapper that passes the current step input through unchanged. */
    final class Identity implements ContextMapper<Object> {
        @Override
        public Object map(StepExecutionContext context) {
            return context.input();
        }
    }
}
