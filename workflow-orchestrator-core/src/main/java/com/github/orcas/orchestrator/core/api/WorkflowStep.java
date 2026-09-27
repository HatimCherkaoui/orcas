package com.github.orcas.orchestrator.core.api;

/** Base type for executable nodes in a workflow graph. */
public abstract class WorkflowStep {
    /** The annotation-derived step name, or the concrete class name when unnamed. */
    public String name() {
        return StepNames.of(this);
    }
}
