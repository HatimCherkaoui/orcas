package com.github.orcas.orchestrator.core.model;

/**
 * Thread-bound execution scope used by framework adapters that cannot receive the
 * current step context directly. The core implementation deliberately has no logging
 * dependency; observability adapters may mirror this scope into MDC or tracing APIs.
 */
public final class WorkflowContextHolder {
    public record Execution(String workflowId, String workflow, WorkflowContext context,
                            StepExecutionContext step) {
    }

    private static final ThreadLocal<Execution> CURRENT = new ThreadLocal<>();

    private WorkflowContextHolder() {
    }

    public static void set(WorkflowContext context) {
        CURRENT.set(new Execution(null, null, context, null));
    }

    public static void set(String workflowId, String workflow, WorkflowContext context) {
        CURRENT.set(new Execution(workflowId, workflow, context, null));
    }

    public static void set(String workflowId, String workflow, StepExecutionContext step) {
        CURRENT.set(new Execution(workflowId, workflow, step.workflowContext(), step));
    }

    public static WorkflowContext current() {
        var execution = CURRENT.get();
        return execution == null ? null : execution.context();
    }

    public static StepExecutionContext step() {
        var execution = CURRENT.get();
        return execution == null ? null : execution.step();
    }

    public static Execution execution() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs an action inside a bound execution scope and always restores the previous scope. */
    public static <T> T with(Execution execution, java.util.function.Supplier<T> action) {
        var previous = CURRENT.get();
        CURRENT.set(execution);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
