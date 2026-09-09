package com.github.orcas.orchestrator.core.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Default {@link WorkflowErrorCategorizer}: treats common transient I/O failures
 * (connection/read timeouts, connection refused, generic {@link IOException}s) and
 * transient HTTP responses (408, 425, 429, 5xx) as {@link ErrorDisposition#REPLAYABLE},
 * and everything else as {@link ErrorDisposition#SUSPEND}, requiring operator
 * intervention or a manual replay.
 */
public final class DefaultWorkflowErrorCategorizer implements WorkflowErrorCategorizer {
    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowErrorCategorizer.class);

    @Override
    public WorkflowError classify(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectException ||
                    current instanceof TimeoutException || current instanceof IOException) {
                log.debug("Classified {} as a transient/replayable error", error.getClass().getSimpleName());
                return new WorkflowError(ErrorDisposition.REPLAYABLE, "transient I/O error", error);
            }
            current = current.getCause();
        }
        log.debug("Classified {} as a non-replayable error", error.getClass().getSimpleName());
        return new WorkflowError(ErrorDisposition.SUSPEND, "non-replayable exception", error);
    }

    @Override
    public WorkflowError classifyResponse(int statusCode) {
        if (statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500) {
            log.debug("Classified HTTP {} as a transient/replayable response", statusCode);
            return new WorkflowError(ErrorDisposition.REPLAYABLE, "transient HTTP response " + statusCode, null);
        }
        log.debug("Classified HTTP {} as a non-replayable response", statusCode);
        return new WorkflowError(ErrorDisposition.SUSPEND, "non-replayable HTTP response " + statusCode, null);
    }
}
