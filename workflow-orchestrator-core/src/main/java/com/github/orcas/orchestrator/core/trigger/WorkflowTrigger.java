package com.github.orcas.orchestrator.core.trigger;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

/** Starts a workflow from an external stimulus without coupling core to its transport. */
@FunctionalInterface
public interface WorkflowTrigger {
    /**
     * Starts or dispatches the named workflow.
     *
     * @param workflow workflow name
     * @param context business input and metadata
     */
    void trigger(String workflow, WorkflowContext context);
}
