package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;

/** Handles a response produced by a transport adapter. */
@FunctionalInterface
public interface ResponseConsumer<R> {
    void consume(StepExecutionContext context, R response);

    /** Stateless sentinel meaning that no response consumer was configured. */
    final class Void implements ResponseConsumer<Object> {
        @Override
        public void consume(StepExecutionContext context, Object response) {
        }
    }
}
