package com.github.orcas.orchestrator.core.model;

import org.slf4j.MDC;

/**
 * Thread-local holder that makes the {@link PipelineContext} and, when available, the
 * {@link StepExecutionContext} of the workflow step currently executing on the calling
 * thread accessible to collaborators that are not part of the explicit call chain
 * (for example AOP aspects or {@code Step} implementations that need the raw HTTP
 * headers captured in the pipeline metadata).
 *
 * <p>As a side effect, every {@code set} overload also populates the SLF4J
 * {@link MDC} with {@code workflowId}, {@code workflow} and {@code step} entries.
 * This gives every log line emitted while a step executes consistent correlation
 * fields, which is exactly what structured-logging backends such as Elasticsearch/
 * Kibana or Grafana Loki expect in order to let operators filter and group log
 * streams per workflow instance or per step. Combined with Micrometer Tracing
 * (trace/span ids, also exposed via MDC), this gives full request correlation across
 * logs, metrics and traces ("the three pillars of observability").
 *
 * <p>Callers <strong>must</strong> invoke {@link #clear()} in a {@code finally} block
 * once the unit of work completes, otherwise stale entries leak across thread-pool
 * reuse (both in this holder and in the MDC).
 */
public final class WorkflowContextHolder {
    /** MDC key holding the current workflow instance id. */
    public static final String MDC_WORKFLOW_ID = "workflowId";
    /** MDC key holding the current workflow definition name. */
    public static final String MDC_WORKFLOW = "workflow";
    /** MDC key holding the name of the step currently executing. */
    public static final String MDC_STEP = "step";

    public record Execution(String workflowId, String workflow, PipelineContext context, StepExecutionContext step) {}
    private static final ThreadLocal<Execution> CURRENT = new ThreadLocal<>();

    private WorkflowContextHolder() {}

    /**
     * Binds only the business {@link PipelineContext} to the current thread, without any
     * workflow/step identity. Used before a workflow instance id has been assigned.
     */
    public static void set(PipelineContext context) {
        CURRENT.set(new Execution(null, null, context, null));
        MDC.remove(MDC_WORKFLOW_ID);
        MDC.remove(MDC_WORKFLOW);
        MDC.remove(MDC_STEP);
    }

    /**
     * Binds the given workflow instance and its business context to the current thread.
     *
     * @param workflowId unique id of the running workflow instance
     * @param workflow   name of the workflow definition
     * @param context    the business pipeline context for this instance
     */
    public static void set(String workflowId, String workflow, PipelineContext context) {
        CURRENT.set(new Execution(workflowId, workflow, context, null));
        putMdc(workflowId, workflow, null);
    }

    /**
     * Binds the given workflow instance and the execution context of the step that is
     * about to run on the current thread.
     *
     * @param workflowId unique id of the running workflow instance
     * @param workflow   name of the workflow definition
     * @param step       execution context of the step about to run
     */
    public static void set(String workflowId, String workflow, StepExecutionContext step) {
        CURRENT.set(new Execution(workflowId, workflow, step.workflowContext(), step));
        putMdc(workflowId, workflow, step.stepName());
    }

    /** Returns the business context bound to the current thread, or {@code null} if none. */
    public static PipelineContext current() {
        var e = CURRENT.get();
        return e == null ? null : e.context();
    }

    /** Returns the step execution context bound to the current thread, or {@code null} if none. */
    public static StepExecutionContext step() {
        var e = CURRENT.get();
        return e == null ? null : e.step();
    }

    /** Returns the full {@link Execution} bound to the current thread, or {@code null} if none. */
    public static Execution execution() { return CURRENT.get(); }

    /**
     * Clears the thread-local binding and the associated MDC entries. Must always be
     * paired with a prior {@code set(...)} call, typically in a {@code finally} block.
     */
    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_WORKFLOW_ID);
        MDC.remove(MDC_WORKFLOW);
        MDC.remove(MDC_STEP);
    }

    private static void putMdc(String workflowId, String workflow, String step) {
        if (workflowId != null) MDC.put(MDC_WORKFLOW_ID, workflowId); else MDC.remove(MDC_WORKFLOW_ID);
        if (workflow != null) MDC.put(MDC_WORKFLOW, workflow); else MDC.remove(MDC_WORKFLOW);
        if (step != null) MDC.put(MDC_STEP, step); else MDC.remove(MDC_STEP);
    }
}
