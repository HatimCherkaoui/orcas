package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;

public interface WorkflowStateStore {
    void start(String id, String workflow, PipelineContext context);

    void record(StatusEvent event);

    default void record(StatusEvent event, String stepTypeClassName) {
        record(event);
    }

    default String workflowName(String id) { throw new UnsupportedOperationException("workflowName not implemented"); }

    PipelineContext context(String id);

    default StepContext stepContext(String workflowId, String stepName) { return null; }

    default void saveStepContext(StepContext context) { }

    /**
     * Records a single retry attempt for {@code stepName} of workflow instance
     * {@code workflowId}: increments its persisted retry counter and appends a
     * {@code RETRY} entry to the step's audit log, so the dashboard can show how
     * many times a step has been retried and why. No-op by default so state stores
     * that don't support retry tracking (e.g. in-memory/test implementations)
     * don't need to implement it.
     *
     * @param workflowId workflow instance id
     * @param stepName   name of the step being retried
     * @param attempt    the 1-based delivery/replay attempt number, if known (0 if not applicable)
     * @param reason     a short human-readable reason for the retry (e.g. the root error message)
     */
    default void recordRetry(String workflowId, String stepName, int attempt, String reason) { }

    void updateContext(String id, PipelineContext context);

    default void finish(StatusEvent event) {
    }
}

