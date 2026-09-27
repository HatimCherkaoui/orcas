package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.engine.WorkflowObserver;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Logs workflow lifecycle events and mirrors execution ids into MDC. */
public final class WorkflowMdcObserver implements WorkflowObserver {
    private static final Logger log = LoggerFactory.getLogger(WorkflowMdcObserver.class);
    private final WorkflowObservabilityProperties properties;

    public WorkflowMdcObserver(WorkflowObservabilityProperties properties) { this.properties = properties; }

    @Override public void onStart(String workflowId, String workflow) {
        if (!properties.isEvents()) return;
        log.debug("Workflow started: {} ({})", workflowId, workflow);
    }

    @Override public void onEvent(StatusEvent event) {
        if (!properties.isEvents()) return;
        log.debug("Workflow event: {} / {} / {}", event.workflowId(), event.step(), event.status());
    }

    @Override public void onStepStart(StepExecutionContext execution, WorkflowStep step) {
        if (properties.isMdc()) bind(execution);
        if (properties.isEvents()) log.debug("Workflow step started: {}", step.name());
    }

    @Override public void onStepEnd(StepExecutionContext execution, WorkflowStep step, StatusEvent event) {
        if (properties.isEvents()) log.debug("Workflow step finished: {} -> {}", step.name(), event.status());
        if (properties.isMdc()) clear();
    }

    @Override public void onFailure(StepExecutionContext execution, WorkflowStep step, Throwable error) {
        if (properties.isMdc()) bind(execution);
        if (properties.isEvents()) log.debug("Workflow step failed: {}", step.name(), error);
        if (properties.isMdc()) clear();
    }

    private void bind(StepExecutionContext execution) {
        MDC.put("workflowId", execution.workflowId());
        MDC.put("workflow", execution.workflow());
        MDC.put("workflowStep", execution.stepName());
    }

    private void clear() {
        MDC.remove("workflowId");
        MDC.remove("workflow");
        MDC.remove("workflowStep");
    }
}
