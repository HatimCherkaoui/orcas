package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.Optional;

/** Persistence contract required by the workflow engine. */
public interface WorkflowStateStore {
    void start(String workflowId, String workflow, WorkflowContext context);

    void record(StatusEvent event, String stepTypeClassName);

    String workflowName(String workflowId);

    WorkflowContext context(String workflowId);

    Optional<StepContext> stepContext(String workflowId, String stepName);

    void saveStepContext(StepContext context);

    void updateContext(String workflowId, WorkflowContext context);

    void finish(StatusEvent event);
}
