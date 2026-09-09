package com.github.orcas.demo.config;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

@Workflow("order-pipeline")
public class OrderWorkflow {
    @WorkflowStep("extract-order")
    public PipelineContext extract(PipelineContext context) {
        context.metadata().put("extract-order", "true");
        return context;
    }

    @WorkflowStep("join")
    public StepResult join(PipelineContext context) {
        context.metadata().put("join", "true");
        return StepResult.success(context);
    }

    @WorkflowStep(value = "notify", async = true)
    public StepResult notifyAsync(StepExecutionContext execution) {
        execution.attribute("notification", "async");
        execution.workflowContext().metadata().put("notified", "true");
        return StepResult.success(execution.workflowContext());
    }
}
