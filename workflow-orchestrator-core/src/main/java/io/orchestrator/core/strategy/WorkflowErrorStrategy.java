package com.github.orcas.orchestrator.core.error;

/**
 * Strategy interface for categorizing workflow errors.
 *
 * This is a small, explicit extension point that can be bridged by existing
 * {@link WorkflowErrorCategorizer} implementations.
 */
public interface WorkflowErrorStrategy {

  /**
   * Error category classification.
   */
  enum ErrorCategory {
    /** Error is replayable - step can be retried */
    REPLAYABLE,

    /** Error is non-replayable - step should not be retried */
    NON_REPLAYABLE,

    /** Circuit breaker is open - step execution is blocked */
    CIRCUIT_BREAKER_OPEN
  }

  /**
   * Determine if an exception can be retried.
   *
   * @param ex The exception to evaluate
   * @return true if step can be retried, false otherwise
   */
  boolean canRetry(Throwable ex);

  /**
   * Categorize an exception.
   *
   * @param ex The exception to categorize
   * @return The error category
   */
  ErrorCategory categorize(Throwable ex);

}


