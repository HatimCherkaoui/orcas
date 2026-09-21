package com.github.orcas.demo.config;

import com.github.orcas.demo.workflows.OrderWorkflow;
import com.github.orcas.orchestrator.core.builder.*;
import com.github.orcas.orchestrator.core.model.Status;
import org.springframework.stereotype.Component;

@Component
public class WorkflowDefinitions implements WorkflowDefinitionProvider {
    private final StepCatalog steps;

    public WorkflowDefinitions(StepCatalog steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowDefinition workflow() {
        return new PipelineBuilder(OrderWorkflow.class, steps)
                .initialize().on(StatusCriteria.onStart()).then("extract-order")
                .parallel().when(StatusCriteria.status("extract-order", Status.SUCCESS))
                .and("customer-call")
                .and("inventory-call")
                .then("join").end()
                .sequential().when(StatusCriteria.status("join", Status.SUCCESS))
                .then("archive-call")
                .async().when(StatusCriteria.status("join", Status.SUCCESS))
                .and("notify").end()
                .build();
    }
}
