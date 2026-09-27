package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepResultsTest {
    private final WorkflowContext context = WorkflowContext.of("order-42");

    @Test
    void keepsAnExistingStepResultUntouched() {
        var result = StepResult.failed(context, "blocked");

        assertThat(StepResults.from(context, result)).isSameAs(result);
    }

    @Test
    void convertsAWorkflowContextIntoSuccess() {
        var next = WorkflowContext.of("order-43");

        var result = StepResults.from(context, next);

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.context()).isSameAs(next);
    }

    @Test
    void convertsPlainValuesIntoBusinessInput() {
        var result = StepResults.from(context, "accepted");

        assertThat(result.context().businessInput()).isEqualTo("accepted");
        assertThat(result.context().metadata()).isSameAs(context.metadata());
    }

    @Test
    void treatsNullAsSuccessfulUnchangedContext() {
        var result = StepResults.from(context, null);

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.context()).isSameAs(context);
    }
}
