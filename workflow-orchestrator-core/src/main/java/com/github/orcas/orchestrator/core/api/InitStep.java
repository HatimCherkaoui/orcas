package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;


/** Synthetic node used by the event-driven engine to start routing. */
@WorkflowStep(StepNames.INIT)
public final class InitStep extends com.github.orcas.orchestrator.core.api.WorkflowStep {
}
