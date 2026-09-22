package com.github.orcas.orchestrator.core.model;

import java.util.Map;

public record WorkflowContext(Object businessInput, Metadata metadata) {
    public WorkflowContext {
        if (metadata == null) metadata = new Metadata();
    }

    public static WorkflowContext of(Object input) {
        return new WorkflowContext(input, new Metadata());
    }

    public static WorkflowContext of(Object input, Map<String, String> headers) {
        return new WorkflowContext(input, new Metadata(headers));
    }

    public WorkflowContext withBusinessInput(Object input) {
        return new WorkflowContext(input, metadata);
    }
}
