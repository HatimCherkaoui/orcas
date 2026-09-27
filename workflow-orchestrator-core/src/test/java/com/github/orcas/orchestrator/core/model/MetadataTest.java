package com.github.orcas.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataTest {
    @Test
    void copiesInitialValuesAndExposesAnImmutableSnapshot() {
        var metadata = new Metadata(Map.of("traceId", "trace-1"));

        metadata.put("tenant", "orcas");
        var snapshot = metadata.asMap();

        assertThat(snapshot).containsEntry("traceId", "trace-1");
        assertThat(snapshot).containsEntry("tenant", "orcas");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshot.put("another", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ignoresNullEntries() {
        var metadata = new Metadata();

        metadata.put(null, "value");
        metadata.put("key", null);

        assertThat(metadata.asMap()).isEmpty();
    }
}
