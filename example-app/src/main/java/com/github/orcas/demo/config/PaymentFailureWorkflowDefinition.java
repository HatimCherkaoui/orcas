package com.github.orcas.demo.config;

import com.github.orcas.orchestrator.core.builder.*;
import com.github.orcas.orchestrator.core.model.Status;
import org.springframework.stereotype.Component;

@Component
public class PaymentFailureWorkflowDefinition implements WorkflowDefinitionProvider {
    private final StepCatalog steps;

    public PaymentFailureWorkflowDefinition(StepCatalog steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowDefinition workflow() {
        return new PipelineBuilder(PaymentCallbackWorkflow.Failure.class, steps)
                .initialize().on(StatusCriteria.onStart()).then("fail-payment")
                .async().when(StatusCriteria.status("fail-payment", Status.SUCCESS))
                .then("notify-payment-failed").end()
                .build();
    }
}
