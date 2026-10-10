package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCriteriaTest {
    private static final String WORKFLOW_ID = "wf-1";

    @Test
    void matchesExpectedStepAndStatus() {
        var criteria = StatusCriteria.success(ValidateStep.class);
        var success = event("validate", Status.SUCCESS);
        var failure = event("validate", Status.FAILED);

        assertThat(criteria.matches(success)).isTrue();
        assertThat(criteria.matches(failure)).isFalse();
        assertThat(criteria.expectedStep()).isEqualTo("validate");
        assertThat(criteria.waitsFor(event("validate", Status.RUNNING))).isTrue();
        assertThat(criteria.waitsFor(failure)).isFalse();
    }

    private static StatusEvent event(String step, Status status) {
        return StatusEvent.of(WORKFLOW_ID, "orders", step, status, Map.of(), status.name());
    }

    @WorkflowStep("validate")
    static final class ValidateStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }
}
