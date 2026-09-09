package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;

/**
 * A step whose work is dispatched to the engine's async executor. The workflow
 * immediately publishes a {@code RUNNING_ASYNC} status while
 * {@link #executeAsync(PipelineContext)} runs in the background, allowing the caller
 * thread (e.g. a Kafka listener) to return promptly.
 */
public abstract class AsyncStep extends WorkflowStep {
    /**
     * Executes the step asynchronously.
     *
     * @param context the pipeline context at this point in the workflow
     * @return the outcome of the step, including the (possibly updated) context
     * @throws Exception any failure; classified the same way as {@link Step#execute}
     */
    public abstract StepResult executeAsync(PipelineContext context) throws Exception;
}
