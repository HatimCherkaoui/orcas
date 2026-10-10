package com.github.orcas.orchestrator.core.trigger;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Objects;

/** Simple framework-neutral trigger adapter around the engine. */
public final class WorkflowLauncher implements WorkflowTrigger {
    private final WorkflowEngine engine;

    public WorkflowLauncher(WorkflowEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void trigger(String workflow, WorkflowContext context) {
        engine.start(workflow, context);
    }
}
