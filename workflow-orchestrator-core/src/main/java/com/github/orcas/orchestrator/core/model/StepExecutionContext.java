package com.github.orcas.orchestrator.core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime view available to every workflow step.
 * workflowContext is the original/shared workflow context; parentStepContext is
 * the persisted output of the step that triggered this step.
 */
public final class StepExecutionContext {
    private final String workflowId;
    private final String workflow;
    private final String stepName;
    private final PipelineContext workflowContext;
    private final StepContext parentStepContext;
    private final Object input;
    private volatile Object output;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public StepExecutionContext(String workflowId, String workflow, String stepName,
                                PipelineContext workflowContext, StepContext parentStepContext,
                                Object input) {
        this.workflowId = workflowId;
        this.workflow = workflow;
        this.stepName = stepName;
        this.workflowContext = workflowContext;
        this.parentStepContext = parentStepContext;
        this.input = input;
    }

    public String workflowId() { return workflowId; }
    public String workflow() { return workflow; }
    public String stepName() { return stepName; }
    public PipelineContext workflowContext() { return workflowContext; }
    public StepContext parentStepContext() { return parentStepContext; }
    public Object input() { return input; }
    public Object output() { return output; }
    public void output(Object value) { this.output = value; }
    public Map<String, Object> attributes() { return attributes; }
    public StepExecutionContext attribute(String key, Object value) {
        if (key != null && value != null) attributes.put(key, value);
        return this;
    }
}
