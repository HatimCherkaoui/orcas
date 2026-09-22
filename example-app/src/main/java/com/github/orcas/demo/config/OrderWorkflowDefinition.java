package com.github.orcas.demo.config;

import com.github.orcas.orchestrator.core.builder.*;
import com.github.orcas.orchestrator.core.model.Status;
import org.springframework.stereotype.Component;

@Component
public class OrderWorkflowDefinition implements WorkflowDefinitionProvider {
    private final StepCatalog steps;

    public OrderWorkflowDefinition(StepCatalog steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowDefinition workflow() {
        return new PipelineBuilder(OrderWorkflow.class, steps)
                .initialize().on(StatusCriteria.onStart()).then("validate-and-reserve")
                .sequential().when(StatusCriteria.status("validate-and-reserve", Status.SUCCESS)).then("load-order")
                .sequential().when(StatusCriteria.status("load-order", Status.SUCCESS)).then("initiate-payment")
                .build();
    }
}
