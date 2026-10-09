package com.github.orcas.orchestrator.core.error;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Conservative default classification for common transient failures.
 */
public final class DefaultWorkflowErrorCategorizer implements WorkflowErrorCategorizer {
    @Override
    public WorkflowError classify(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException || current instanceof IOException) {
                return new WorkflowError(ErrorDisposition.REPLAYABLE, "transient I/O error", error);
            }
        }
        String reason = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "non-replayable exception"
                : error.getMessage();
        return new WorkflowError(ErrorDisposition.SUSPEND, reason, error);
    }

    @Override
    public WorkflowError classifyResponse(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
                ? new WorkflowError(ErrorDisposition.REPLAYABLE, "transient HTTP response " + statusCode, null)
                : new WorkflowError(ErrorDisposition.SUSPEND, "non-replayable HTTP response " + statusCode, null);
    }
}
