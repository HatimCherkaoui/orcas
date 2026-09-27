package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMdcObserverTest {
    private final WorkflowObservabilityProperties properties = new WorkflowObservabilityProperties();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void bindsAndClearsWorkflowIdentityAroundAStep() {
        properties.setMdc(true);
        var observer = new WorkflowMdcObserver(properties);
        var context = new StepExecutionContext(
                "wf-1", "orders", "reserve", WorkflowContext.of("42"), null, "42");
        var step = new ReserveStep();
        var event = new StatusEvent(
                "wf-1", "orders", "reserve", Status.SUCCESS, Map.of(), "done", Instant.now());

        observer.onStepStart(context, step);

        assertThat(MDC.get("workflowId")).isEqualTo("wf-1");
        assertThat(MDC.get("workflow")).isEqualTo("orders");
        assertThat(MDC.get("workflowStep")).isEqualTo("reserve");

        observer.onStepEnd(context, step, event);

        assertThat(MDC.get("workflowId")).isNull();
        assertThat(MDC.get("workflow")).isNull();
        assertThat(MDC.get("workflowStep")).isNull();
    }

    static final class ReserveStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }

        @Override
        public String name() {
            return "reserve";
        }
    }
}
