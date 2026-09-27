package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRegistryTest {
    @Test
    void registersAndReturnsImmutableSnapshot() {
        var registry = new WorkflowRegistry();
        var definition = new WorkflowDefinition("orders", List.of());

        registry.register(definition);

        assertThat(registry.get("orders")).isSameAs(definition);
        assertThat(registry.all()).containsExactly(definition);
    }

    @Test
    void rejectsDuplicateWorkflowNames() {
        var registry = new WorkflowRegistry();
        registry.register(new WorkflowDefinition("orders", List.of()));

        assertThatThrownBy(() -> registry.register(new WorkflowDefinition("orders", List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate workflow");
    }
}
