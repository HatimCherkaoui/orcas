package com.github.orcas.orchestrator.core.model;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime state exposed to the current workflow step and its adapters. */
public final class StepExecutionContext {
    private final String workflowId;
    private final String workflow;
    private final String stepName;
    private final WorkflowContext workflowContext;
    private final StepContext parentStepContext;
    private final Object input;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile Object output;

    public StepExecutionContext(
            String workflowId,
            String workflow,
            String stepName,
            WorkflowContext workflowContext,
            StepContext parentStepContext,
            Object input) {
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.stepName = Objects.requireNonNull(stepName, "stepName");
        this.workflowContext = Objects.requireNonNull(workflowContext, "workflowContext");
        this.parentStepContext = parentStepContext;
        this.input = input;
    }

    public String workflowId() {
        return workflowId;
    }

    public String workflow() {
        return workflow;
    }

    public String stepName() {
        return stepName;
    }

    public WorkflowContext workflowContext() {
        return workflowContext;
    }

    public StepContext parentStepContext() {
        return parentStepContext;
    }

    public Object input() {
        return input;
    }

    public Object output() {
        return output;
    }

    public void output(Object value) {
        this.output = value;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public StepExecutionContext attribute(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
        return this;
    }
}
