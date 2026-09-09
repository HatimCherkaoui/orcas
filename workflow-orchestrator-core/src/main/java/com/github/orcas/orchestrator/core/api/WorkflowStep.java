package com.github.orcas.orchestrator.core.api;

/**
 * Base type of every executable node in a workflow graph: a uniquely named unit of
 * work resolved by {@link com.github.orcas.orchestrator.core.builder.StepCatalog} and wired
 * into a {@link com.github.orcas.orchestrator.core.builder.WorkflowDefinition} via
 * {@link com.github.orcas.orchestrator.core.builder.PipelineBuilder}.
 *
 * <p>Concrete steps extend either {@link Step} (synchronous) or {@link AsyncStep}
 * (fire-and-continue, completed later via a callback event) rather than this class
 * directly.
 */
public abstract class WorkflowStep {
    /** @return the unique step name used for routing and to correlate log/audit entries */
    public abstract String name();
}
