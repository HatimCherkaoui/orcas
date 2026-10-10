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
    private final com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry;
    @Override public com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry() { return telemetry; }

    public WorkflowMdcObserver(WorkflowObservabilityProperties properties) { this(properties, com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.noop()); }
    public WorkflowMdcObserver(WorkflowObservabilityProperties properties, com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { this.properties = properties; this.telemetry = telemetry; }

    @Override public void onStart(String workflowId, String workflow) {
        if (!properties.isEvents()) return;
        log.debug("Workflow started: {} ({})", workflowId, workflow);
    }

    @Override public void onEvent(StatusEvent event) {
        if (!properties.isEvents()) return;
        log.debug("Workflow event: {} / {} / {}", event.workflowId(), event.step(), event.status());
    }

    @Override public void onStepStart(StepExecutionContext execution, WorkflowStep step) {
        logged(execution, () -> { if (properties.isEvents()) log.info("Workflow step started: {}", step.name()); });
    }

    @Override public void onStepEnd(StepExecutionContext execution, WorkflowStep step, StatusEvent event) {
        logged(execution, () -> { if (properties.isEvents()) log.info("Workflow step finished: {} -> {}", step.name(), event.status()); });
    }

    @Override public void onFailure(StepExecutionContext execution, WorkflowStep step, Throwable error) {
        io.opentelemetry.api.trace.Span.current().recordException(error);
        io.opentelemetry.api.trace.Span.current().setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
        logged(execution, () -> { if (properties.isEvents()) log.warn("Workflow step failed: {}", step.name(), error); });
    }

    private void logged(StepExecutionContext execution, Runnable action) {
        var previous = MDC.getCopyOfContextMap();
        try { if (properties.isMdc()) bind(execution); action.run(); }
        finally { if (previous == null) MDC.clear(); else MDC.setContextMap(previous); }
    }
    private void bind(StepExecutionContext execution) {
        execution.workflowContext().metadata().identifiers().forEach(MDC::put);
        MDC.put("workflowId", execution.workflowId());
        MDC.put("workflow", execution.workflow());
        MDC.put("workflowStep", execution.stepName());
    }
    private void clear() {
        com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.keys().forEach(MDC::remove);
        MDC.remove("workflowId"); MDC.remove("workflow"); MDC.remove("workflowStep");
    }
}
