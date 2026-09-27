package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.Steps;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineBuilderTest {
    @Workflow("orders")
    static class Orders {
    }

    @WorkflowStep("validate")
    static final class Validate extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }

    @WorkflowStep("reserve")
    static final class Reserve extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }

    @Test
    void composesFunctionalStepsDirectly() {
        var step = Steps.step("validate", context -> context.businessInput());

        var definition = PipelineBuilder.forWorkflow(Orders.class, new StepCatalog(List.of()))
                .initialize()
                .on(StatusCriteria.init())
                .then(step)
                .build();

        assertThat(definition.routes().getFirst().steps()).containsExactly(step);
    }

    @Test
    void buildsByStepTypeWithoutRepeatingStepNames() {
        var catalog = new StepCatalog(List.of(new Validate(), new Reserve()));

        var definition = PipelineBuilder.forWorkflow(Orders.class, catalog)
                .initialize()
                .on(StatusCriteria.init())
                .then(Validate.class)
                .sequential()
                .when(StatusCriteria.success(Validate.class))
                .then(Reserve.class)
                .build();

        assertThat(definition.name()).isEqualTo("orders");
        assertThat(definition.routes()).hasSize(2);
    }

    @Test
    void rejectsParallelRouteWithoutBranches() {
        assertThatThrownBy(() -> PipelineBuilder.forWorkflow(Orders.class, new StepCatalog(List.of()))
                .parallel()
                .when(StatusCriteria.init())
                .then(Steps.step("join", context -> context.businessInput())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Parallel route requires at least one branch");
    }
}
