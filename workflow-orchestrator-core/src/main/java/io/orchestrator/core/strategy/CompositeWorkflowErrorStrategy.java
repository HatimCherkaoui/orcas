package com.github.orcas.orchestrator.core.error;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.lang.reflect.Method;

/**
 * Composite error strategy that chains multiple strategies.
 * First matching strategy determines the categorization.
 */
public class CompositeWorkflowErrorStrategy implements WorkflowErrorStrategy {

  private final java.util.List<WorkflowErrorStrategy> strategies;

  public CompositeWorkflowErrorStrategy(java.util.List<WorkflowErrorStrategy> strategies) {
    this.strategies = strategies;
  }

  @Override
  public boolean canRetry(Throwable ex) {
    for (WorkflowErrorStrategy strategy : strategies) {
      // If any strategy says yes, we can retry
      if (strategy.canRetry(ex)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ErrorCategory categorize(Throwable ex) {
    for (WorkflowErrorStrategy strategy : strategies) {
      ErrorCategory category = strategy.categorize(ex);
      if (category != null && category != ErrorCategory.NON_REPLAYABLE) {
        return category;
      }
    }
    return ErrorCategory.NON_REPLAYABLE;
  }

  /** I/O Error Strategy: IOException, SocketException, ConnectException = REPLAYABLE. */
  public static class IoErrorStrategy implements WorkflowErrorStrategy {
    @Override
    public boolean canRetry(Throwable ex) {
      return ex instanceof IOException
          || ex instanceof SocketException
          || ex instanceof ConnectException;
    }

    @Override
    public ErrorCategory categorize(Throwable ex) {
      if (canRetry(ex)) {
        return ErrorCategory.REPLAYABLE;
      }
      return null;
    }
  }

  /** HTTP Status Code Strategy: 408, 425, 429, 5xx = REPLAYABLE. */
  public static class HttpErrorStrategy implements WorkflowErrorStrategy {
    private static final java.util.Set<Integer> REPLAYABLE_CODES = java.util.Set.of(
        408, 425, 429, 500, 502, 503, 504
    );

    @Override
    public boolean canRetry(Throwable ex) {
      Integer code = statusCode(ex);
      return code != null && REPLAYABLE_CODES.contains(code);
    }

    @Override
    public ErrorCategory categorize(Throwable ex) {
      if (canRetry(ex)) {
        return ErrorCategory.REPLAYABLE;
      }
      if (statusCode(ex) != null) {
        return ErrorCategory.NON_REPLAYABLE;
      }
      return null;
    }

    private Integer statusCode(Throwable ex) {
      try {
        Method method = ex.getClass().getMethod("getStatusCode");
        Object statusCode = method.invoke(ex);
        if (statusCode == null) {
          return null;
        }
        try {
          Method value = statusCode.getClass().getMethod("value");
          return (Integer) value.invoke(statusCode);
        } catch (ReflectiveOperationException ignored) {
          try {
            Method is5xx = statusCode.getClass().getMethod("is5xxServerError");
            boolean serverError = (Boolean) is5xx.invoke(statusCode);
            if (serverError) {
              return 500;
            }
            Method is4xx = statusCode.getClass().getMethod("is4xxClientError");
            if ((Boolean) is4xx.invoke(statusCode)) {
              return 400;
            }
          } catch (ReflectiveOperationException ignoredToo) {
            return null;
          }
        }
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
      return null;
    }
  }

  /** Timeout Strategy: SocketTimeoutException = REPLAYABLE. */
  public static class TimeoutErrorStrategy implements WorkflowErrorStrategy {
    @Override
    public boolean canRetry(Throwable ex) {
      return ex instanceof SocketTimeoutException
          || (ex instanceof java.util.concurrent.TimeoutException);
    }

    @Override
    public ErrorCategory categorize(Throwable ex) {
      if (canRetry(ex)) {
        return ErrorCategory.REPLAYABLE;
      }
      return null;
    }
  }

  /** Default Strategy: Everything else = NON_REPLAYABLE. */
  public static class DefaultErrorStrategy implements WorkflowErrorStrategy {
    @Override
    public boolean canRetry(Throwable ex) {
      return false;
    }

    @Override
    public ErrorCategory categorize(Throwable ex) {
      return ErrorCategory.NON_REPLAYABLE;
    }
  }

  /** Factory: Create default composite strategy with standard order. */
  public static CompositeWorkflowErrorStrategy createDefault() {
    return new CompositeWorkflowErrorStrategy(java.util.Arrays.asList(
        new IoErrorStrategy(),
        new HttpErrorStrategy(),
        new TimeoutErrorStrategy(),
        new DefaultErrorStrategy()
    ));
  }

}




