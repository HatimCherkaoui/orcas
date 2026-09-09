package com.github.orcas.orchestrator.core.api;

@com.github.orcas.orchestrator.core.annotation.WorkflowStep("INIT")
public class InitStep extends WorkflowStep {
    @Override
    public String name() {
        return StepNames.INIT.name();
    }
}
