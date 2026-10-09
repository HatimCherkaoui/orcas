package com.github.orcas.orchestrator.core.error;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;
import com.github.orcas.orchestrator.core.retry.WorkflowResponseException;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;

/**
 * Semantic classification for HTTP, network, and JDBC failures. Unknown application
 * failures are terminal by default; deployments can supply a custom categorizer.
 */
public final class DefaultWorkflowErrorCategorizer implements WorkflowErrorCategorizer {
    @Override
    public WorkflowError classify(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof WorkflowResponseException response) {
                return response.error();
            }
            if (current instanceof WorkflowRetryableException retryable) {
                return retryable.error();
            }
            if (current instanceof WorkflowSuspendedException suspended) {
                return suspended.error();
            }
            if (current instanceof SQLTransientException || current instanceof SQLRecoverableException) {
                return retryable("DATABASE_TRANSIENT", sqlState(current), describe(current), error);
            }
            if (current instanceof SQLException sql) {
                WorkflowError sqlError = classifySql(sql, error);
                if (sqlError != null) return sqlError;
            }
            if (current instanceof TimeoutException) {
                return retryable("TIMEOUT", null, describe(current), error);
            }
            if (current instanceof IOException) {
                return retryable("NETWORK", null, describe(current), error);
            }
            if (isTransientSpringDataAccessFailure(current)) {
                return retryable("DATABASE_TRANSIENT", null, describe(current), error);
            }
        }
        return new WorkflowError(ErrorDisposition.FAILED, "APPLICATION", error == null ? "UNKNOWN"
                : error.getClass().getSimpleName(),
                describe(error), error);
    }

    @Override
    public WorkflowError classifyResponse(int statusCode) {
        String reason = "HTTP " + statusCode + " " + httpReason(statusCode);
        String category = switch (statusCode) {
            case 408 -> "HTTP_REQUEST_TIMEOUT";
            case 425 -> "HTTP_TOO_EARLY";
            case 429 -> "HTTP_RATE_LIMITED";
            case 500 -> "HTTP_INTERNAL_SERVER_ERROR";
            case 502 -> "HTTP_BAD_GATEWAY";
            case 503 -> "HTTP_SERVICE_UNAVAILABLE";
            case 504 -> "HTTP_GATEWAY_TIMEOUT";
            default -> statusCode >= 400 && statusCode < 500 ? "HTTP_CLIENT_ERROR" : "HTTP_SERVER_ERROR";
        };
        ErrorDisposition disposition = switch (statusCode) {
            case 408, 425, 429, 502, 503, 504 -> ErrorDisposition.REPLAYABLE;
            default -> ErrorDisposition.FAILED;
        };
        return new WorkflowError(disposition, category, "HTTP " + statusCode, reason, null);
    }

    private WorkflowError classifySql(SQLException sql, Throwable original) {
        String state = sql.getSQLState();
        if (state == null) return null;
        if (state.startsWith("08") || state.startsWith("40") || state.startsWith("53") || state.equals("55P03")
                || state.equals("57P01") || state.equals("57P02") || state.equals("57P03")) {
            return retryable(state.startsWith("08") ? "DATABASE_CONNECTION" : "DATABASE_TRANSACTION",
                    state, describe(sql), original);
        }
        if (state.startsWith("23") || state.startsWith("22") || state.startsWith("42")) {
            return new WorkflowError(ErrorDisposition.FAILED, "DATABASE_PERMANENT", state,
                    describe(sql), original);
        }
        return null;
    }

    private static boolean isTransientSpringDataAccessFailure(Throwable error) {
        for (Class<?> type = error.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getSimpleName();
            if (name.equals("TransientDataAccessException")
                    || name.equals("RecoverableDataAccessException")
                    || name.equals("DataAccessResourceFailureException")
                    || name.equals("CannotGetJdbcConnectionException")
                    || name.equals("QueryTimeoutException")
                    || name.equals("CannotAcquireLockException")
                    || name.equals("DeadlockLoserDataAccessException")
                    || name.equals("CannotCreateTransactionException")) return true;
        }
        return false;
    }

    private static WorkflowError retryable(String category, String code, String reason, Throwable cause) {
        return new WorkflowError(ErrorDisposition.REPLAYABLE, category, code, reason, cause);
    }

    private static String sqlState(Throwable error) {
        return error instanceof SQLException sql ? sql.getSQLState() : null;
    }

    private static String describe(Throwable error) {
        if (error == null) return "Unknown workflow error";
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() > 300 ? normalized.substring(0, 297) + "..." : normalized;
    }

    private static String httpReason(int statusCode) {
        return switch (statusCode) {
            case 408 -> "Request Timeout";
            case 425 -> "Too Early";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> statusCode >= 400 && statusCode < 500 ? "Client Error" : "Server Error";
        };
    }
}
