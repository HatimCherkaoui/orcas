package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionTest {
    private final ValidateStep validate = new ValidateStep();
    private final ReserveStep reserve = new ReserveStep();
    private final WorkflowDefinition definition = new WorkflowDefinition("orders", List.of(
            new WorkflowDefinition.Route(StatusCriteria.init(), List.of(validate), null, null),
            new WorkflowDefinition.Route(StatusCriteria.success(validate.getClass()), List.of(reserve), null, null)));

    @Test
    void findsStepsByAnnotationDerivedName() {
        assertThat(definition.findStep("validate")).contains(validate);
        assertThat(definition.findStep("reserve")).contains(reserve);
        assertThat(definition.findStep("missing")).isEmpty();
    }

    @Test
    void matchesRoutesWithoutMutatingDefinition() {
        var event = StatusEvent.of("wf-1", "orders", "validate", Status.SUCCESS, Map.of(), "done");

        assertThat(definition.matching(event)).hasSize(1);
        assertThat(definition.matching(event).getFirst().steps()).containsExactly(reserve);
        assertThat(definition.waitingFor(new StatusEvent(
                "wf-1", "orders", "validate", Status.RUNNING, Map.of(), "running", Instant.now()))).isTrue();
    }


    @Test
    void rejectsHalfConfiguredJoin() {
        assertThatThrownBy(() -> new WorkflowDefinition.Route(
                StatusCriteria.init(),
                List.of(validate),
                reserve,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joinStep and joinKey");
    }

    @Test
    void exposesJoinPresenceWithoutDuplicatingNullChecks() {
        var joinRoute = new WorkflowDefinition.Route(
                StatusCriteria.init(),
                List.of(validate),
                reserve,
                "order");
        assertThat(joinRoute.hasJoin()).isTrue();
        assertThat(definition.routes().getFirst().hasJoin()).isFalse();
    }

    @WorkflowStep("validate")
    static final class ValidateStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }

    @WorkflowStep("reserve")
    static final class ReserveStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }
}
