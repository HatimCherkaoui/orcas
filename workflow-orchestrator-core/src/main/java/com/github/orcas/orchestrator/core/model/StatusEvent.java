package com.github.orcas.orchestrator.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable workflow lifecycle event used for routing, persistence and observation. */
public record StatusEvent(
        String workflowId,
        String workflow,
        String step,
        Status status,
        Map<String, String> metadata,
        String message,
        Instant timestamp) {

    public StatusEvent {
        workflowId = requireText(workflowId, "workflowId");
        workflow = requireText(workflow, "workflow");
        step = requireText(step, "step");
        Objects.requireNonNull(status, "status");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static StatusEvent of(
            String workflowId,
            String workflow,
            String step,
            Status status,
            Map<String, String> metadata,
            String message) {
        return new StatusEvent(workflowId, workflow, step, status, metadata, message, Instant.now());
    }

    public static String newPipelineId() {
        return UUID.randomUUID().toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
