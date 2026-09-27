package com.github.orcas.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepExecutionContextTest {
    @Test
    void supportsFluentAttributesAndMutableOutput() {
        var context = WorkflowContext.of("order-1");
        var execution = new StepExecutionContext(
                "wf-1", "orders", "reserve", context, null, "order-1");

        var returned = execution.attribute("attempt", 2);
        execution.output("reserved");

        assertThat(returned).isSameAs(execution);
        assertThat(execution.attributes()).containsEntry("attempt", 2);
        assertThat(execution.output()).isEqualTo("reserved");
    }
}
