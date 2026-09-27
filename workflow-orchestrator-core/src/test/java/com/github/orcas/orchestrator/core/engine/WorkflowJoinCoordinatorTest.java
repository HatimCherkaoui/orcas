package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.StatusCriteria;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowJoinCoordinatorTest {
    private final WorkflowStep first = step("first");
    private final WorkflowStep second = step("second");
    private final WorkflowStep join = step("join");

    @Test
    void startsJoinOnlyAfterAllBranchesSucceed() {
        var route = new WorkflowDefinition.Route(
                StatusCriteria.init(),
                List.of(first, second),
                join,
                "payment");
        var coordinator = new WorkflowJoinCoordinator();
        coordinator.register("wf-1", route);
        var started = new ArrayList<WorkflowStep>();

        coordinator.accept(success("wf-1", "first"), (event, step) -> started.add(step));
        assertThat(started).isEmpty();

        coordinator.accept(success("wf-1", "second"), (event, step) -> started.add(step));
        assertThat(started).containsExactly(join);
    }

    @Test
    void ignoresUnrelatedWorkflowEvents() {
        var route = new WorkflowDefinition.Route(
                StatusCriteria.init(),
                List.of(first, second),
                join,
                "payment");
        var coordinator = new WorkflowJoinCoordinator();
        coordinator.register("wf-1", route);

        assertThat(coordinator.isRelated(success("wf-2", "first"))).isFalse();
        assertThat(coordinator.isRelated(success("wf-1", "unknown"))).isFalse();
        assertThat(coordinator.isRelated(success("wf-1", "first"))).isTrue();
    }

    @Test
    void ignoresNonSuccessBranchEvents() {
        var route = new WorkflowDefinition.Route(
                StatusCriteria.init(),
                List.of(first, second),
                join,
                "payment");
        var coordinator = new WorkflowJoinCoordinator();
        coordinator.register("wf-1", route);
        var started = new ArrayList<WorkflowStep>();

        coordinator.accept(
                new StatusEvent("wf-1", "orders", "first", Status.FAILED, Map.of(), "failed", Instant.now()),
                (event, step) -> started.add(step));

        assertThat(started).isEmpty();
    }

    private static WorkflowStep step(String name) {
        return new Step() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public StepResult execute(WorkflowContext context) {
                return StepResult.success(context);
            }
        };
    }

    private static StatusEvent success(String workflowId, String step) {
        return StatusEvent.of(workflowId, "orders", step, Status.SUCCESS, Map.of(), "done");
    }
}
