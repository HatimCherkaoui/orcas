package com.github.orcas.orchestrator.core.error;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/** Conservative default classification for common transient failures. */
public final class DefaultWorkflowErrorCategorizer implements WorkflowErrorCategorizer {
    @Override
    public WorkflowError classify(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectException
                    || current instanceof TimeoutException || current instanceof IOException) {
                return new WorkflowError(ErrorDisposition.REPLAYABLE, "transient I/O error", error);
            }
        }
        return new WorkflowError(ErrorDisposition.SUSPEND, "non-replayable exception", error);
    }

    @Override
    public WorkflowError classifyResponse(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
                ? new WorkflowError(ErrorDisposition.REPLAYABLE, "transient HTTP response " + statusCode, null)
                : new WorkflowError(ErrorDisposition.SUSPEND, "non-replayable HTTP response " + statusCode, null);
    }
}
