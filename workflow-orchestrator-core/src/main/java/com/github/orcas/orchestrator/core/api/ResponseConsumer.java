package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;


@FunctionalInterface
public interface ResponseConsumer<R> {

    /**
     * Consumes the response of a step execution.
     *
     * @param context  the step execution context
     * @param response the response of the step execution
     */
    void consume(StepExecutionContext context, R response);


    public class Void implements ResponseConsumer<Object> {
        @Override
        public void consume(StepExecutionContext context, Object response) {
        }
    }
}
