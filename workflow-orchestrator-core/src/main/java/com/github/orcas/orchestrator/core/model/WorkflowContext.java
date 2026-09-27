package com.github.orcas.orchestrator.core.model;

import java.util.Map;

/** Business input plus transport-neutral workflow metadata. */
public record WorkflowContext(Object businessInput, Metadata metadata) {

    public WorkflowContext {
        metadata = metadata == null ? new Metadata() : metadata;
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
