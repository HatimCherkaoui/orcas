package com.github.orcas.demo.config;

import com.github.orcas.orchestrator.core.builder.*;
import com.github.orcas.orchestrator.core.model.Status;
import org.springframework.stereotype.Component;

@Component
public class PaymentSuccessWorkflowDefinition implements WorkflowDefinitionProvider {
    private final StepCatalog steps;

    public PaymentSuccessWorkflowDefinition(StepCatalog steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowDefinition workflow() {
        return new PipelineBuilder(PaymentCallbackWorkflow.Success.class, steps)
                .initialize().on(StatusCriteria.onStart()).then("confirm-payment")
                .async().when(StatusCriteria.status("confirm-payment", Status.SUCCESS))
                .then("notify-payment-success").end()
                .build();
    }
}
