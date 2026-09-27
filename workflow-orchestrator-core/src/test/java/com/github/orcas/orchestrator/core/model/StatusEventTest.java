package com.github.orcas.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusEventTest {
    @Test
    void copiesMetadataSoTheEventIsImmutable() {
        var metadata = new HashMap<String, String>();
        metadata.put("correlationId", "corr-1");

        var event = new StatusEvent(
                "wf-1", "orders", "reserve", Status.SUCCESS,
                metadata, "done", Instant.now());
        metadata.put("another", "value");

        assertThat(event.metadata())
                .containsExactly(Map.entry("correlationId", "corr-1"));
    }

    @Test
    void suppliesTimestampWhenNoneWasProvided() {
        var event = new StatusEvent(
                "wf-1", "orders", "reserve", Status.SUCCESS,
                Map.of(), null, null);

        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void rejectsMissingIdentityFields() {
        assertThatThrownBy(() -> new StatusEvent(
                "", "orders", "reserve", Status.SUCCESS, Map.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflowId");
    }
}
