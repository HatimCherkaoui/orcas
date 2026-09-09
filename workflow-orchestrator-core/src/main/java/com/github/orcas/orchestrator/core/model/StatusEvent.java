package com.github.orcas.orchestrator.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StatusEvent(String workflowId, String workflow, String step, Status status, Map<String, String> metadata,
                          String message, Instant timestamp) {
    public static StatusEvent of(String id, String w, String s, Status st, Map<String, String> m, String msg) {
        return new StatusEvent(id, w, s, st, m, msg, Instant.now());
    }

    public static String newPipelineId() {
        return UUID.randomUUID().toString();
    }
}
