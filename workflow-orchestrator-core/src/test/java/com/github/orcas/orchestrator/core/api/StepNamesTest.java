package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StepNamesTest {
    @Workflow("orders")
    static class Orders {
        @WorkflowStep("reserve")
        void reserve() {
        }
    }

    @Workflow
    static class NamedWorkflow {
    }

    static class PlainStep extends com.github.orcas.orchestrator.core.api.WorkflowStep {
    }

    @Test
    void usesExplicitWorkflowName() {
        assertThat(StepNames.workflow(Orders.class)).isEqualTo("orders");
    }

    @Test
    void fallsBackToWorkflowClassName() {
        assertThat(StepNames.workflow(NamedWorkflow.class)).isEqualTo("NamedWorkflow");
    }

    @Test
    void derivesStepNameFromClass() {
        assertThat(StepNames.of(PlainStep.class)).isEqualTo("PlainStep");
    }

    @Test
    void derivesStepNameFromMethodAnnotation() throws Exception {
        Method method = Orders.class.getDeclaredMethod("reserve");

        assertThat(StepNames.of(method)).isEqualTo("reserve");
    }
}
