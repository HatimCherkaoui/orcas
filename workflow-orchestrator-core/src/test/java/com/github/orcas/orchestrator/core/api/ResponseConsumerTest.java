package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ResponseConsumerTest {
    @Test
    void defaultConsumerAcceptsAnyResponseWithoutDoingAnything() {
        var context = new StepExecutionContext(
                "wf-1", "orders", "payment", WorkflowContext.of("order-1"), null, "order-1");

        assertThatCode(() -> new ResponseConsumer.Void().consume(context, "provider-response"))
                .doesNotThrowAnyException();
    }
}
