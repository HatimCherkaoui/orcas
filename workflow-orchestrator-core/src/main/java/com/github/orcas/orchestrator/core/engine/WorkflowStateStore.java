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

    void updateContext(String id, PipelineContext context);

    default void finish(StatusEvent event) {
    }
}
