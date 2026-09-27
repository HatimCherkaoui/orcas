package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

/** Synchronous workflow step. */
public abstract class Step extends WorkflowStep {
    public abstract StepResult execute(WorkflowContext context) throws Exception;
}
