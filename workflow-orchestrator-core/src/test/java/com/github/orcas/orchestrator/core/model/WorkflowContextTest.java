package com.github.orcas.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowContextTest {
    @Test
    void preservesMetadataWhenBusinessInputChanges() {
        var context = WorkflowContext.of("order-1", Map.of("correlationId", "corr-1"));

        var updated = context.withBusinessInput(Map.of("status", "READY"));

        assertThat(updated.businessInput()).isEqualTo(Map.of("status", "READY"));
        assertThat(updated.metadata()).isSameAs(context.metadata());
        assertThat(updated.metadata().get("correlationId")).isEqualTo("corr-1");
    }

    @Test
    void createsSafeEmptyMetadataForNull() {
        var context = new WorkflowContext("order-1", null);

        assertThat(context.metadata()).isNotNull();
        assertThat(context.metadata().identifiers()).containsKeys("requestId","correlationId","transactionId","traceId");
    }
}
