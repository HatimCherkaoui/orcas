package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryWorkflowStateStoreTest {
    @Test
    void storesWorkflowAndStepContext() {
        var store = new InMemoryWorkflowStateStore();
        var context = WorkflowContext.of(Map.of("orderId", 7));
        store.start("wf-1", "orders", context);

        var step = new StepContext("wf-1", "orders", "reserve", null,
                context.businessInput(), "done", Map.of(), Instant.now());
        store.saveStepContext(step);

        assertThat(store.workflowName("wf-1")).isEqualTo("orders");
        assertThat(store.context("wf-1")).isSameAs(context);
        assertThat(store.stepContext("wf-1", "reserve")).contains(step);

        store.record(new StatusEvent("wf-1", "orders", "reserve",
                Status.SUSPENDED, Map.of(), "blocked", Instant.now()), StepContext.class.getName());
        assertThat(store.suspendedWorkflowIds("reserve")).containsExactly("wf-1");
    }
}
