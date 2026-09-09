package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;

/**
 * A synchronous workflow step, executed on the calling thread (the {@code WorkflowEngine}'s
 * event-handling thread, e.g. a Kafka consumer thread). Implementations should be
 * side-effect-idempotent where possible, since a failed step may be replayed.
 */
public abstract class Step extends WorkflowStep {
    /**
     * Executes the step against the current business context.
     *
     * @param context the pipeline context (business input + metadata) at this point
     *                in the workflow
     * @return the outcome of the step, including the (possibly updated) context
     * @throws Exception any failure; classified by a
     *                    {@link com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer}
     *                    to decide between an automatic replay or suspending the workflow
     */
    public abstract StepResult execute(PipelineContext context) throws Exception;
}
