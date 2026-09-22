package com.github.orcas.demo.config;

import com.github.orcas.orchestrator.core.builder.*;
import com.github.orcas.orchestrator.core.model.Status;
import org.springframework.stereotype.Component;

@Component
public class PaymentRefundWorkflowDefinition implements WorkflowDefinitionProvider {
    private final StepCatalog steps;

    public PaymentRefundWorkflowDefinition(StepCatalog steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowDefinition workflow() {
        return new PipelineBuilder(PaymentCallbackWorkflow.Refund.class, steps)
                .initialize().on(StatusCriteria.onStart()).then("refund-record")
                .sequential().when(StatusCriteria.status("refund-record", Status.SUCCESS)).then("refund-payment")
                .sequential().when(StatusCriteria.status("refund-payment", Status.SUCCESS)).then("refund-completed")
                .build();
    }
}
