package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

/** Lifecycle hook for logging, metrics and tracing adapters. */
public interface WorkflowObserver {
    default void onStart(String workflowId, String workflow) {
    }

    default void onEvent(StatusEvent event) {
    }

    default void onStepStart(StepExecutionContext execution, WorkflowStep step) {
    }

    default void onStepEnd(StepExecutionContext execution, WorkflowStep step, StatusEvent event) {
    }

    default void onFailure(StepExecutionContext execution, WorkflowStep step, Throwable error) {
    }

    static WorkflowObserver noop() {
        return new WorkflowObserver() {
        };
    }
}
