package com.github.orcas.orchestrator.core.error;

import java.time.Duration;

/** Classifies execution failures and HTTP responses into engine actions. */
public interface WorkflowErrorCategorizer {
    WorkflowError classify(Throwable error);

    WorkflowError classifyResponse(int statusCode);

    default WorkflowError classifyResponse(int statusCode, String responseMessage) {
        return classifyResponse(statusCode, responseMessage, null);
    }

    default WorkflowError classifyResponse(int statusCode, String responseMessage, Duration retryAfter) {
        WorkflowError classified = classifyResponse(statusCode);
        String reason = responseMessage == null || responseMessage.isBlank()
                ? classified.reason() : classified.reason() + ": " + responseMessage;
        return new WorkflowError(classified.disposition(), classified.category(), classified.code(),
                reason, classified.cause(), retryAfter);
    }
}
