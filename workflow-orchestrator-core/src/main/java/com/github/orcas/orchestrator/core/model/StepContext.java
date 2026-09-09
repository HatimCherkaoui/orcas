package com.github.orcas.orchestrator.core.model;

import java.time.Instant;
import java.util.Map;

/** Persistable snapshot of one step, deliberately independent from the workflow context. */
public record StepContext(
        String workflowId,
        String workflow,
        String stepName,
        String parentStepName,
        Object input,
        Object output,
        Map<String, Object> attributes,
        Instant updatedAt) {

    public StepContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
