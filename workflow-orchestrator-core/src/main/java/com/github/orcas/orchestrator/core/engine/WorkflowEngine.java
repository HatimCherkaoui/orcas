package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.*;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.error.WorkflowError;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.core.retry.WorkflowResponseException;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core execution engine of the orchestrator. Receives {@link StatusEvent}s (either the
 * synthetic {@code INIT} event produced by {@link #start(String, PipelineContext)} or
 * events emitted by completed steps), resolves which {@link WorkflowStep}s should run
 * next according to the {@link WorkflowDefinition} routing table, and executes them.
 *
 * <p>All lifecycle transitions are logged through SLF4J with the workflow id, workflow
 * name and step name attached as {@link MDC} fields (see {@link WorkflowContextHolder}),
 * so downstream log aggregation tools (Elastic/Kibana, Grafana Loki, etc.) can filter
 * and correlate every log line to a specific workflow execution. Lifecycle milestones
 * (start, routing, suspension, failure) are logged at {@code INFO}/{@code WARN}/
 * {@code ERROR}; step-routing internals are logged at {@code DEBUG} to keep production
 * logs at {@code INFO} readable while still allowing operators to raise verbosity for a
 * single logger package at runtime (e.g. via the Spring Boot Actuator {@code /loggers}
 * endpoint) when troubleshooting.
 */
public final class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowRegistry registry;
    private final WorkflowEventPublisher publisher;
    private final WorkflowStateStore store;
    private final Executor asyncExecutor;
    private final WorkflowErrorCategorizer categorizer;

    private record JoinState(Set<String> expected, Set<String> completed, WorkflowStep joinStep) {
        JoinState(Set<String> expected, WorkflowStep joinStep) {
            this(expected, ConcurrentHashMap.newKeySet(), joinStep);
        }
    }

    private final ConcurrentMap<String, JoinState> joins = new ConcurrentHashMap<>();

    /**
     * Creates an engine that classifies unhandled step exceptions using the
     * {@link DefaultWorkflowErrorCategorizer}.
     *
     * @param registry      resolves workflow names to their {@link WorkflowDefinition}
     * @param publisher     publishes {@link StatusEvent}s produced by step execution
     * @param store         persists pipeline/step state and audit trail
     * @param asyncExecutor executor used to run {@link AsyncStep}s and parallel branches
     */
    public WorkflowEngine(WorkflowRegistry registry, WorkflowEventPublisher publisher, WorkflowStateStore store, Executor asyncExecutor) {
        this(registry, publisher, store, asyncExecutor, new DefaultWorkflowErrorCategorizer());
    }

    /**
     * Creates an engine with a custom {@link WorkflowErrorCategorizer}, allowing callers
     * to control which exceptions are treated as transient (replayable) versus terminal
     * (workflow suspended, requiring manual or scheduled replay).
     *
     * @param registry      resolves workflow names to their {@link WorkflowDefinition}
     * @param publisher     publishes {@link StatusEvent}s produced by step execution
     * @param store         persists pipeline/step state and audit trail
     * @param asyncExecutor executor used to run {@link AsyncStep}s and parallel branches
     * @param categorizer   classifies step failures as replayable or terminal
     */
    public WorkflowEngine(WorkflowRegistry registry, WorkflowEventPublisher publisher, WorkflowStateStore store,
                          Executor asyncExecutor, WorkflowErrorCategorizer categorizer) {
        this.registry = registry;
        this.publisher = publisher;
        this.store = store;
        this.asyncExecutor = asyncExecutor;
        this.categorizer = categorizer;
    }

    /**
     * Starts a new instance of the named workflow. Allocates a fresh pipeline id,
     * persists the initial state and publishes the synthetic {@code INIT} event that
     * kicks off routing.
     *
     * @param workflow name of the registered {@link WorkflowDefinition} to start
     * @param context  initial business context and metadata for the new instance
     * @throws IllegalArgumentException if no workflow is registered under that name
     */
    public void start(String workflow, PipelineContext context) {
        if (registry.get(workflow) == null) {
            log.warn("Rejected start request for unknown workflow '{}'", workflow);
            throw new IllegalArgumentException("Unknown workflow: " + workflow);
        }
        String id = StatusEvent.newPipelineId();
        MDC.put(WorkflowContextHolder.MDC_WORKFLOW_ID, id);
        MDC.put(WorkflowContextHolder.MDC_WORKFLOW, workflow);
        try {
            log.info("Starting workflow '{}' as instance {}", workflow, id);
            store.start(id, workflow, context);
            publisher.publish(StatusEvent.of(id, workflow, StepNames.INIT.name(), Status.INIT, context.metadata().asMap(), "workflow started"));
        } finally {
            MDC.remove(WorkflowContextHolder.MDC_WORKFLOW_ID);
            MDC.remove(WorkflowContextHolder.MDC_WORKFLOW);
        }
    }

    /**
     * Convenience overload that builds a {@link PipelineContext} from an HTTP request
     * body and headers before delegating to {@link #start(String, PipelineContext)}.
     *
     * @param workflow name of the registered workflow to start
     * @param body     deserialized request body used as business input
     * @param headers  HTTP request headers, captured into the pipeline metadata
     */
    public void startFromHttpPost(String workflow, Object body, java.util.Map<String, String> headers) {
        start(workflow, PipelineContext.of(body, headers));
    }

    /**
     * Manually replays a previously suspended (or otherwise stalled) step. Typically
     * invoked by an operator via the admin API, or automatically by a retry scheduler
     * after a circuit breaker re-closes.
     *
     * @param workflowId id of the workflow instance to replay
     * @param stepName   name of the step to re-execute
     * @throws IllegalArgumentException if the step is not part of the workflow definition
     */
    public void replay(String workflowId, String stepName) {
        PipelineContext context = store.context(workflowId);
        String workflow = store.workflowName(workflowId);
        var definition = registry.get(workflow);
        var step = definition.findStep(stepName);
        if (step == null) {
            log.warn("Replay requested for unknown step '{}' on workflow instance {}", stepName, workflowId);
            throw new IllegalArgumentException("Unknown step: " + stepName);
        }
        log.info("Replaying step '{}' for workflow instance {}", stepName, workflowId);
        execute(new StatusEvent(workflowId, workflow, stepName, Status.SUSPENDED, context.metadata().asMap(), "manual replay", java.time.Instant.now()), step);
    }

    /**
     * Handles an incoming {@link StatusEvent}: records it in the state store, resolves
     * matching routes from the workflow's routing table, joins parallel branches when
     * applicable, and executes the next step(s).
     *
     * @param event the status event to process (either {@code INIT} or produced by a
     *              previously executed step)
     * @throws IllegalArgumentException if the event refers to an unknown workflow
     */
    public void handle(StatusEvent event) {
        MDC.put(WorkflowContextHolder.MDC_WORKFLOW_ID, event.workflowId());
        MDC.put(WorkflowContextHolder.MDC_WORKFLOW, event.workflow());
        MDC.put(WorkflowContextHolder.MDC_STEP, event.step());
        try {
            log.debug("Handling event step='{}' status={}", event.step(), event.status());
            WorkflowDefinition definition = registry.get(event.workflow());
            if (definition == null) {
                log.warn("Rejected event for unknown workflow '{}'", event.workflow());
                throw new IllegalArgumentException("Unknown workflow: " + event.workflow());
            }
            var incomingStep = definition.findStep(event.step());
            store.record(event, incomingStep == null ? null : incomingStep.getClass().getName());
            boolean joinRelated = joins.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith(event.workflowId() + ":") && entry.getValue().expected().contains(event.step()));
            handleJoin(event);
            var routes = definition.matching(event);
            log.debug("Resolved {} matching route(s) for step='{}' status={}", routes.size(), event.step(), event.status());
            if (routes.isEmpty() && definition.waitingFor(event)
                    && event.status() != Status.RUNNING
                    && event.status() != Status.SUSPENDED) {
                log.debug("Step '{}' is waiting on a join partner; deferring", event.step());
                throw new com.github.orcas.orchestrator.core.retry.WorkflowCriteriaNotMatchedException(event);
            }
            if (routes.isEmpty() && !joinRelated && !definition.waitingFor(event) && event.status() != Status.RUNNING && event.status() != Status.SUSPENDED) {
                log.info("Workflow instance {} reached a terminal state with status {}", event.workflowId(), event.status());
                store.finish(event);
            }
            dispatch(event, routes);
        } finally {
            MDC.remove(WorkflowContextHolder.MDC_WORKFLOW_ID);
            MDC.remove(WorkflowContextHolder.MDC_WORKFLOW);
            MDC.remove(WorkflowContextHolder.MDC_STEP);
        }
    }

    private void dispatch(StatusEvent event, java.util.List<WorkflowDefinition.Route> routes) {
        for (var route : routes) {
            if (route.joinStep() != null)
                joins.putIfAbsent(event.workflowId() + ":" + route.joinKey(), new JoinState(route.steps().stream().map(WorkflowStep::name).collect(Collectors.toSet()), route.joinStep()));
            if (route.joinStep() != null && route.steps().size() > 1) {
                for (WorkflowStep step : route.steps()) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            execute(event, step);
                        } catch (RuntimeException ignored) {
                            // The step publishes its terminal/suspended state; do not fail the parent Kafka delivery.
                        }
                    }, asyncExecutor);
                }
            } else {
                for (WorkflowStep step : route.steps()) execute(event, step);
            }
        }
    }

    private void handleJoin(StatusEvent event) {
        for (var entry : joins.entrySet()) {
            if (!entry.getKey().startsWith(event.workflowId() + ":")) continue;
            JoinState join = entry.getValue();
            if (event.status() == Status.SUCCESS && join.expected().contains(event.step())) {
                join.completed().add(event.step());
                log.debug("Join branch '{}' completed ({}/{})", event.step(), join.completed().size(), join.expected().size());
                if (join.completed().containsAll(join.expected())) {
                    joins.remove(entry.getKey());
                    log.debug("All parallel branches completed; executing join step '{}'", join.joinStep().name());
                    execute(event, join.joinStep());
                }
            }
        }
    }


    private void execute(StatusEvent previous, WorkflowStep step) {
        PipelineContext context = store.context(previous.workflowId());
        var parent = store.stepContext(previous.workflowId(), previous.step());
        var execution = new com.github.orcas.orchestrator.core.model.StepExecutionContext(
                previous.workflowId(), previous.workflow(), step.name(), context, parent, context.businessInput());

        if (step instanceof AsyncStep async) {
            log.info("Starting async step '{}'", step.name());
            publisher.publish(StatusEvent.of(previous.workflowId(), previous.workflow(), step.name(), Status.RUNNING_ASYNC,
                    context.metadata().asMap(), "async step started"));
            CompletableFuture.supplyAsync(() -> {
                        WorkflowContextHolder.set(previous.workflowId(), previous.workflow(), execution);
                        try {
                            return async.executeAsync(context);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        } finally {
                            WorkflowContextHolder.clear();
                        }
                    }, asyncExecutor)
                    .thenAcceptAsync(result -> {
                        execution.output(result.context().businessInput());
                        store.saveStepContext(snapshot(execution));
                        store.updateContext(previous.workflowId(), result.context());
                        log.info("Async step '{}' completed with status {}", async.name(), result.status());
                        publisher.publish(StatusEvent.of(previous.workflowId(), previous.workflow(), async.name(), result.status(),
                                result.context().metadata().asMap(), result.message()));
                    }, asyncExecutor)
                    .exceptionally(throwable -> {
                        Throwable cause = unwrap(throwable);
                        log.error("Async step '{}' failed: {}", step.name(), cause.toString(), cause);
                        handleFailure(previous, step, cause, context);
                        return null;
                    });
            return;
        }
        try {
            WorkflowContextHolder.set(previous.workflowId(), previous.workflow(), execution);
            log.info("Executing step '{}'", step.name());
            StepResult result = ((Step) step).execute(context);
            execution.output(result.context().businessInput());
            store.saveStepContext(snapshot(execution));
            store.updateContext(previous.workflowId(), result.context());
            log.info("Step '{}' finished with status {}", step.name(), result.status());
            publisher.publish(StatusEvent.of(previous.workflowId(), previous.workflow(), step.name(), result.status(),
                    result.context().metadata().asMap(), result.message()));
            if (result.status() == Status.FAILED)
                throw new WorkflowRetryableException(step.name(), "Step returned FAILED");
        } catch (WorkflowRetryableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Step '{}' threw an exception: {}", step.name(), e.toString());
            store.saveStepContext(snapshot(execution));
            throw new WorkflowRetryableException(step.name(), categorizer.classify(e));
        } finally {
            WorkflowContextHolder.clear();
        }
    }

    private com.github.orcas.orchestrator.core.model.StepContext snapshot(
            com.github.orcas.orchestrator.core.model.StepExecutionContext e) {
        String parent = e.parentStepContext() == null ? null : e.parentStepContext().stepName();
        return new com.github.orcas.orchestrator.core.model.StepContext(
                e.workflowId(), e.workflow(), e.stepName(), parent,
                e.input(), e.output(), e.attributes(), java.time.Instant.now());
    }

    private void handleFailure(StatusEvent previous, WorkflowStep step, Throwable error, PipelineContext context) {
        if (error instanceof WorkflowResponseException response) {
            if (response.error().replayable()) throw new CompletionException(response);
            suspend(previous, step, response.error());
            return;
        }
        WorkflowError classification = error instanceof WorkflowSuspendedException suspended ? suspended.error() : categorizer.classify(error);
        if (classification.replayable())
            throw new CompletionException(new WorkflowRetryableException(step.name(), classification));
        suspend(previous, step, classification);
    }

    private void suspend(StatusEvent previous, WorkflowStep step, WorkflowError error) {
        log.warn("Suspending workflow instance {} at step '{}': {}", previous.workflowId(), step.name(), error.reason());
        publisher.publish(StatusEvent.of(previous.workflowId(), previous.workflow(), step.name(), Status.SUSPENDED,
                store.context(previous.workflowId()).metadata().asMap(), error.reason()));
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : t;
    }
}
