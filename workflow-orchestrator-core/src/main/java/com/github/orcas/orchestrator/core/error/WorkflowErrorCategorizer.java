package com.github.orcas.orchestrator.core.error;

/** Classifies execution failures and HTTP responses into engine actions. */
public interface WorkflowErrorCategorizer {
    WorkflowError classify(Throwable error);

    WorkflowError classifyResponse(int statusCode);
}
