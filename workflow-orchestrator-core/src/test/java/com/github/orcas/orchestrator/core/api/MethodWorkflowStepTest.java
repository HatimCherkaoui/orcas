package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodWorkflowStepTest {
    @Test
    void usesAnnotationNameAndInvokesBusinessInputMethod() throws Exception {
        var adapter = new MethodWorkflowStep(new Target(),
                Target.class.getDeclaredMethod("reserve", String.class));

        var result = adapter.execute(WorkflowContext.of("order-7"));

        assertThat(adapter.name()).isEqualTo("reserve-order");
        assertThat(result.context().businessInput()).isEqualTo("reserved:order-7");
    }

    static final class Target {
        @WorkflowStep("reserve-order")
        String reserve(String orderId) {
            return "reserved:" + orderId;
        }
    }
}
