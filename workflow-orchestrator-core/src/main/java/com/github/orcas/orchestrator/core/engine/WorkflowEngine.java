package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.retry.WorkflowCriteriaNotMatchedException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Pure Java workflow engine. Integrations are supplied through small extension contracts. */
public final class WorkflowEngine {
    private final WorkflowRegistry registry;
    private final WorkflowEventPublisher publisher;
    private final WorkflowStateStore store;
    private final Executor asyncExecutor;
    private final WorkflowObserver observer;
    private final WorkflowStepExecutor stepExecutor;
    private final WorkflowJoinCoordinator joins;

    /** Creates an engine with the default error classifier and no-op observer. */
    public WorkflowEngine(
            WorkflowRegistry registry,
            WorkflowEventPublisher publisher,
            WorkflowStateStore store,
            Executor asyncExecutor) {
        this(
                registry,
                publisher,
                store,
                asyncExecutor,
                new DefaultWorkflowErrorCategorizer(),
                WorkflowObserver.noop());
    }

    /** Creates an engine with a custom error classifier and no-op observer. */
    public WorkflowEngine(
            WorkflowRegistry registry,
            WorkflowEventPublisher publisher,
            WorkflowStateStore store,
            Executor asyncExecutor,
            WorkflowErrorCategorizer categorizer) {
        this(registry, publisher, store, asyncExecutor, categorizer, WorkflowObserver.noop());
    }

    /** Creates an engine with explicit execution and lifecycle policies. */
    public WorkflowEngine(
            WorkflowRegistry registry,
            WorkflowEventPublisher publisher,
            WorkflowStateStore store,
            Executor asyncExecutor,
            WorkflowErrorCategorizer categorizer,
            WorkflowObserver observer) {
        this(registry, publisher, store, asyncExecutor, categorizer, observer, null);
    }

    /** Creates an engine with explicit error and automatic retry policies. */
    public WorkflowEngine(
            WorkflowRegistry registry,
            WorkflowEventPublisher publisher,
            WorkflowStateStore store,
            Executor asyncExecutor,
            WorkflowErrorCategorizer categorizer,
            WorkflowObserver observer,
            WorkflowRetryCoordinator retryCoordinator) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.store = Objects.requireNonNull(store, "store");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        var errorCategorizer = Objects.requireNonNull(categorizer, "categorizer");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.joins = new WorkflowJoinCoordinator();
        this.stepExecutor = new WorkflowStepExecutor(
                store,
                publisher,
                asyncExecutor,
                errorCategorizer,
                observer,
                retryCoordinator);
    }

    /** Starts a new workflow instance and publishes its initial event. */
    public void start(String workflow, WorkflowContext context) {
        requireDefinition(workflow);
        String id = StatusEvent.newPipelineId();
        observer.onStart(id, workflow);
        store.start(id, workflow, context);
        publish(StatusEvent.of(
                id,
                workflow,
                StepNames.INIT,
                Status.INIT,
                context.metadata().asMap(),
                "workflow started"));
    }

    /** Starts a new workflow asynchronously and completes when the start event has been published. */
    public CompletableFuture<Void> startAsync(String workflow, WorkflowContext context) {
        requireDefinition(workflow);
        return CompletableFuture.runAsync(() -> start(workflow, context), asyncExecutor);
    }

    /** Re-executes a known step for an existing workflow instance. */
    public void replay(String workflowId, String stepName) {
        var context = store.context(workflowId);
        var workflow = store.workflowName(workflowId);
        var definition = requireDefinition(workflow);
        var step = definition.findStep(stepName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown step: " + stepName));
        stepExecutor.execute(StatusEvent.of(
                workflowId,
                workflow,
                stepName,
                Status.SUSPENDED,
                context.metadata().asMap(),
                "manual replay"), step);
    }

    /** Consumes one event and dispatches all matching route steps. */
    public void handle(StatusEvent event) {
        var definition = requireDefinition(event.workflow());
        var incomingStep = definition.findStep(event.step());
        observer.onEvent(event);
        store.record(event, incomingStep.map(step -> step.getClass().getName()).orElse(null));

        boolean joinRelated = joins.isRelated(event);
        joins.accept(event, stepExecutor::execute);

        var routes = definition.matching(event);
        if (routes.isEmpty() && definition.waitingFor(event) && isTerminal(event)) {
            throw new WorkflowCriteriaNotMatchedException(event);
        }
        if (shouldFinish(event, definition, routes, joinRelated)) {
            store.finish(event);
        }
        dispatch(event, routes);
    }

    private void dispatch(StatusEvent event, java.util.List<WorkflowDefinition.Route> routes) {
        routes.forEach(route -> {
            joins.register(event.workflowId(), route);
            if (route.hasJoin() && route.steps().size() > 1) {
                route.steps().forEach(step -> executeInBackground(event, step));
                return;
            }
            route.steps().forEach(step -> stepExecutor.execute(event, step));
        });
    }

    private void executeInBackground(StatusEvent event, WorkflowStep step) {
        CompletableFuture.runAsync(() -> stepExecutor.execute(event, step), asyncExecutor);
    }

    private static boolean shouldFinish(
            StatusEvent event,
            WorkflowDefinition definition,
            java.util.List<WorkflowDefinition.Route> routes,
            boolean joinRelated) {
        return routes.isEmpty()
                && !joinRelated
                && !definition.waitingFor(event)
                && isTerminal(event);
    }

    private static boolean isTerminal(StatusEvent event) {
        return event.status() != Status.RUNNING && event.status() != Status.SUSPENDED;
    }

    private void publish(StatusEvent event) {
        observer.onEvent(event);
        publisher.publish(event);
    }

    private WorkflowDefinition requireDefinition(String workflow) {
        var definition = registry.get(workflow);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflow);
        }
        return definition;
    }
}
