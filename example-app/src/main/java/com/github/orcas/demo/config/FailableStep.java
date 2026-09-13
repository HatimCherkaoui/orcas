package com.github.orcas.demo.config;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.TimeoutException;

@WorkflowStep(value = "tofail", async = true)
public class FailableStep extends Step {

    @Override
    public StepResult execute(PipelineContext context) throws Exception {

        var bool = new Random().nextBoolean();
        context.metadata().put("timeout", Boolean.toString(bool));
        System.out.println("TOFAIL : Timeout flag: " + context.metadata().get("timeout"));
        if (Boolean.parseBoolean(context.metadata().get("timeout"))) {
            throw new TimeoutException("timeout");
        }

        return StepResult.success(context);
    }

    @Override
    public String name() {
        return "tofail";
    }
}
