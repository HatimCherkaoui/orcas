package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;

/** Maps a transport result into a workflow result. */
@FunctionalInterface
public interface ResultMapper<O> {
    O map(StepExecutionContext context, Object response) throws Exception;
}
