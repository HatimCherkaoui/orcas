package com.github.orcas.orchestrator.core.trigger;

import com.github.orcas.orchestrator.core.model.PipelineContext;

/**
 * Strategy for starting a workflow instance from some external stimulus (an inbound
 * HTTP request, a queue message, a scheduled/lambda invocation, a file dropped over
 * SSH/SFTP, ...). Marker sub-interfaces ({@link RestTrigger}, {@link QueueTrigger},
 * {@link LambdaTrigger}, {@link SshFileTrigger}) exist purely to let dependency
 * injection frameworks distinguish trigger flavors when several are registered.
 */
@FunctionalInterface
public interface WorkflowTrigger {
    /**
     * Starts (or otherwise dispatches to) the named workflow with the given context.
     *
     * @param workflow name of the workflow to trigger
     * @param context  business input and metadata for the new instance
     */
    void trigger(String workflow, PipelineContext context);
}
