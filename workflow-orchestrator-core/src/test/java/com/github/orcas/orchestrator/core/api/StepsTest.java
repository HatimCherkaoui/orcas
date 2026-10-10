package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepsTest {
    @Test
    void adaptsFunctionResultIntoBusinessInput() throws Exception {
        var step = Steps.step("uppercase",
                context -> String.valueOf(context.businessInput()).toUpperCase());

        var result = step.execute(WorkflowContext.of("order-42"));

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.context().businessInput()).isEqualTo("ORDER-42");
    }

    @Test
    void derivesTheSuppliedFunctionalStepName() {
        var step = Steps.step("named", context -> context.businessInput());

        assertThat(step.name()).isEqualTo("named");
    }
}
