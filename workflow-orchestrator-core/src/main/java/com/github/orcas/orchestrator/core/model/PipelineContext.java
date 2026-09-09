package com.github.orcas.orchestrator.core.model;

import java.util.Map;

public record PipelineContext(Object businessInput, Metadata metadata) {
    public PipelineContext {
        if (metadata == null) metadata = new Metadata();
    }

    public static PipelineContext of(Object input) {
        return new PipelineContext(input, new Metadata());
    }

    public static PipelineContext of(Object input, Map<String, String> headers) {
        return new PipelineContext(input, new Metadata(headers));
    }

    public PipelineContext withBusinessInput(Object input) {
        return new PipelineContext(input, metadata);
    }
}
