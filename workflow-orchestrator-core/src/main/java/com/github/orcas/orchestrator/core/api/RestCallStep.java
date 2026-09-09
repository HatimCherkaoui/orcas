package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

import java.util.function.Function;

/**
 * Functional REST adapter. The transport is deliberately opaque to core:
 * callers may return ResponseEntity, Mono, Flux or any application type and
 * the result mapper decides how to adapt it.
 */
public final class RestCallStep<I, O> extends Step {
    private final String name;
    private final ContextMapper<I> mapper;
    private final Function<I, ?> caller;
    private final ResultMapper<O> resultMapper;

    public RestCallStep(String name, ContextMapper<I> mapper, Function<I, ?> caller, ResultMapper<O> resultMapper) {
        this.name = name;
        this.mapper = mapper;
        this.caller = caller;
        this.resultMapper = resultMapper;
    }

    @Override public String name() { return name; }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        StepExecutionContext execution = com.github.orcas.orchestrator.core.model.WorkflowContextHolder.step();
        I request = mapper.map(execution);
        Object response = caller.apply(request);
        O output = resultMapper.map(execution, response);
        execution.output(output);
        return StepResult.success(context);
    }
}
