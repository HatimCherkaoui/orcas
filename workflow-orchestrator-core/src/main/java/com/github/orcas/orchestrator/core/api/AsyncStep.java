package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

/** Workflow step executed by the engine's asynchronous executor. */
public abstract class AsyncStep extends WorkflowStep {
    public abstract StepResult executeAsync(WorkflowContext context) throws Exception;
}
