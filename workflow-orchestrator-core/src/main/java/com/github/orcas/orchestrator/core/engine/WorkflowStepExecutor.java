package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.error.WorkflowError;
import com.github.orcas.orchestrator.core.error.ErrorDisposition;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Executes workflow steps and persists/publishes their outcomes. */
final class WorkflowStepExecutor {
    private final WorkflowStateStore store;
    private final WorkflowEventPublisher publisher;
    private final Executor asyncExecutor;
    private final WorkflowErrorCategorizer categorizer;
    private final WorkflowObserver observer;
    private final WorkflowRetryCoordinator retryCoordinator;

    WorkflowStepExecutor(
            WorkflowStateStore store,
            WorkflowEventPublisher publisher,
            Executor asyncExecutor,
            WorkflowErrorCategorizer categorizer,
            WorkflowObserver observer,
            WorkflowRetryCoordinator retryCoordinator) {
        this.store = store;
        this.publisher = publisher;
        this.asyncExecutor = asyncExecutor;
        this.categorizer = categorizer;
        this.observer = observer;
        this.retryCoordinator = retryCoordinator;
    }

    void execute(StatusEvent previous, WorkflowStep step) {
        var context = store.context(previous.workflowId());
        var parent = store.stepContext(previous.workflowId(), previous.step()).orElse(null);
        var execution = new StepExecutionContext(
                previous.workflowId(),
                previous.workflow(),
                step.name(),
                context,
                parent,
                context.businessInput());

        if (step instanceof AsyncStep async) {
            executeAsync(previous, step, async, context, execution);
            return;
        }

        try (var operation = observer.telemetry().begin("Step", "workflow.step " + step.name(), context.metadata(),
                java.util.Map.of("workflowId", previous.workflowId(), "workflow", previous.workflow(), "workflowStep", step.name()))) {
            operation.attribute("status",executeSynchronously(previous, step, context, execution).name());
        }
    }

    private void executeAsync(
            StatusEvent previous,
            WorkflowStep step,
            AsyncStep async,
            WorkflowContext context,
            StepExecutionContext execution) {
        publish(StatusEvent.of(
                previous.workflowId(),
                previous.workflow(),
                step.name(),
                Status.RUNNING_ASYNC,
                context.metadata().asMap(),
                "async step started"));

        CompletableFuture.supplyAsync(() -> {
            var previousExecution = WorkflowContextHolder.execution();
            WorkflowContextHolder.set(previous.workflowId(), previous.workflow(), execution);
            observer.onStepStart(execution, step);
            try (var operation = observer.telemetry().begin("Step", "workflow.step " + step.name(), context.metadata(),
                    java.util.Map.of("workflowId", previous.workflowId(), "workflow", previous.workflow(), "workflowStep", step.name()))) {
                try {
                    var result = async.executeAsync(context);
                    operation.attribute("status",result.status().name());
                    return result;
                } catch (Exception error) { operation.error(error); throw error; }
            } catch (Exception error) {
                throw new CompletionException(error);
            } finally {
                WorkflowContextHolder.restore(previousExecution);
            }
        }, asyncExecutor)
                .thenAcceptAsync(
                        result -> WorkflowContextHolder.with(new WorkflowContextHolder.Execution(previous.workflowId(),previous.workflow(),context,execution), () -> {
                            try (var operation = observer.telemetry().begin("Step","workflow.step.complete " + step.name(),context.metadata(),
                                    java.util.Map.of("workflowId",previous.workflowId(),"workflow",previous.workflow(),"workflowStep",step.name()))) {
                                saveAndPublish(previous,step,execution,result);
                            }
                            return null;
                        }),
                        asyncExecutor)
                .exceptionally(error -> WorkflowContextHolder.with(new WorkflowContextHolder.Execution(previous.workflowId(),previous.workflow(),context,execution), () -> {
                    try (var operation = observer.telemetry().begin("Step","workflow.step.failure " + step.name(),context.metadata(),
                            java.util.Map.of("workflowId",previous.workflowId(),"workflow",previous.workflow(),"workflowStep",step.name()))) {
                        operation.attribute("status","FAILED");
                        handleFailure(previous,step,unwrap(error),context,execution);
                    }
                    return null;
                }));
    }

    private Status executeSynchronously(
            StatusEvent previous,
            WorkflowStep step,
            WorkflowContext context,
            StepExecutionContext execution) {
        var previousExecution = WorkflowContextHolder.execution();
        try {
            WorkflowContextHolder.set(previous.workflowId(), previous.workflow(), execution);
            observer.onStepStart(execution, step);
            StepResult result = ((Step) step).execute(context);
            saveAndPublish(previous, step, execution, result);
            return result.status();
        } catch (WorkflowRetryableException error) {
            observer.onFailure(execution, step, error);
            return publishFailure(previous, step, context, error.error());
        } catch (WorkflowSuspendedException error) {
            observer.onFailure(execution, step, error);
            return publishFailure(previous, step, context, error.error());
        } catch (Exception error) {
            observer.onFailure(execution, step, error);
            return publishFailure(previous, step, context, categorizer.classify(error));
        } finally {
            WorkflowContextHolder.restore(previousExecution);
        }
    }

    private Status publishFailure(StatusEvent previous, WorkflowStep step, WorkflowContext context,
                                WorkflowError error) {
        Status status;
        if (error.disposition() == ErrorDisposition.REPLAYABLE) {
            if (retryCoordinator != null && !retryCoordinator.automaticRetriesEnabled()) {
                error = new WorkflowError(ErrorDisposition.SUSPEND, error.category(), error.code(),
                        "Automatic retries are disabled: " + error.reason(), error.cause(), error.retryAfter());
                status = Status.SUSPENDED;
            } else if (retryCoordinator != null && retryCoordinator.retryAllowed(previous.workflowId(), step.name())) {
                status = Status.SUSPENDED;
            } else if (retryCoordinator == null) {
                error = error.withDisposition(ErrorDisposition.SUSPEND);
                status = Status.SUSPENDED;
            } else {
                error = new WorkflowError(ErrorDisposition.FAILED, error.category(), error.code(),
                        "Automatic retries exhausted: " + error.reason(), error.cause());
                status = Status.FAILED;
            }
        } else {
            status = error.disposition() == ErrorDisposition.SUSPEND ? Status.SUSPENDED : Status.FAILED;
        }

        publish(StatusEvent.failure(previous.workflowId(), previous.workflow(), step.name(), status,
                context.metadata().asMap(), error));
        if (status == Status.SUSPENDED && error.disposition() == ErrorDisposition.REPLAYABLE) {
            retryCoordinator.scheduleRetry(previous.workflowId(), step.name(), error);
        }
        return status;
    }

    private void saveAndPublish(
            StatusEvent previous,
            WorkflowStep step,
            StepExecutionContext execution,
            StepResult result) {
        // A step may return a fresh context; execution identifiers must survive that replacement.
        execution.workflowContext().metadata().identifiers().forEach(result.context().metadata()::put);
        com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.ensure(result.context().metadata());
        execution.output(result.context().businessInput());
        store.saveStepContext(snapshot(execution));
        store.updateContext(previous.workflowId(), result.context());

        var event = StatusEvent.of(
                previous.workflowId(),
                previous.workflow(),
                step.name(),
                result.status(),
                result.context().metadata().asMap(),
                result.message());
        publish(event);
        observer.onStepEnd(execution, step, event);
    }

    private void handleFailure(
            StatusEvent previous,
            WorkflowStep step,
            Throwable error,
            WorkflowContext context,
            StepExecutionContext execution) {
        observer.onFailure(execution, step, error);
        WorkflowError classification = error instanceof WorkflowSuspendedException suspended
                ? suspended.error()
                : error instanceof WorkflowRetryableException retryable
                ? retryable.error()
                : categorizer.classify(error);
        publishFailure(previous, step, context, classification);
    }

    private void publish(StatusEvent event) {
        observer.onEvent(event);
        publisher.publish(event);
    }

    private static StepContext snapshot(StepExecutionContext execution) {
        return new StepContext(
                execution.workflowId(),
                execution.workflow(),
                execution.stepName(),
                execution.parentStepContext() == null
                        ? null
                        : execution.parentStepContext().stepName(),
                execution.input(),
                execution.output(),
                execution.attributes(),
                Instant.now());
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : throwable;
    }
}
