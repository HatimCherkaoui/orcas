package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

/**
 * Minimal database adapter. The application decides whether writer is a JPA repository,
 * JdbcTemplate call, NamedParameterJdbcTemplate, JdbcBatch, stored procedure, etc.
 */
public final class DatabaseLoader<I> extends Step {
    private final String name;
    private final ContextMapper<I> mapper;
    private final DataWriter<I> writer;

    public DatabaseLoader(String name, ContextMapper<I> mapper, DataWriter<I> writer) {
        this.name = name;
        this.mapper = mapper;
        this.writer = writer;
    }

    @Override public String name() { return name; }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        StepExecutionContext execution = com.github.orcas.orchestrator.core.model.WorkflowContextHolder.step();
        I value = mapper.map(execution);
        writer.write(value);
        execution.output(value);
        return StepResult.success(context);
    }
}
