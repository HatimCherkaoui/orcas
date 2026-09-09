package com.github.orcas.orchestrator.core.error;

public interface WorkflowErrorCategorizer {
    WorkflowError classify(Throwable error);
    WorkflowError classifyResponse(int statusCode);
}
