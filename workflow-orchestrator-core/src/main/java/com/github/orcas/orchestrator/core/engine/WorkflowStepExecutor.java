package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.error.WorkflowError;
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

    WorkflowStepExecutor(
            WorkflowStateStore store,
            WorkflowEventPublisher publisher,
            Executor asyncExecutor,
            WorkflowErrorCategorizer categorizer,
            WorkflowObserver observer) {
        this.store = store;
        this.publisher = publisher;
        this.asyncExecutor = asyncExecutor;
        this.categorizer = categorizer;
        this.observer = observer;
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

        executeSynchronously(previous, step, context, execution);
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
            WorkflowContextHolder.set(previous.workflowId(), previous.workflow(), execution);
            observer.onStepStart(execution, step);
            try {
                return async.executeAsync(context);
            } catch (Exception error) {
                throw new CompletionException(error);
            } finally {
                WorkflowContextHolder.clear();
            }
        }, asyncExecutor)
                .thenAcceptAsync(
                        result -> saveAndPublish(previous, step, execution, result),
                        asyncExecutor)
                .exceptionally(error -> {
                    handleFailure(previous, step, unwrap(error), context, execution);
                    return null;
                });
    }

    private void executeSynchronously(
            StatusEvent previous,
            WorkflowStep step,
            WorkflowContext context,
            StepExecutionContext execution) {
        try {
            WorkflowContextHolder.set(previous.workflowId(), previous.workflow(), execution);
            observer.onStepStart(execution, step);
            StepResult result = ((Step) step).execute(context);
            saveAndPublish(previous, step, execution, result);
            if (result.status() == Status.FAILED) {
                throw new WorkflowRetryableException(step.name(), "Step returned FAILED");
            }
        } catch (WorkflowRetryableException | WorkflowSuspendedException error) {
            throw error;
        } catch (Exception error) {
            observer.onFailure(execution, step, error);
            throw new WorkflowRetryableException(step.name(), categorizer.classify(error));
        } finally {
            WorkflowContextHolder.clear();
        }
    }

    private void saveAndPublish(
            StatusEvent previous,
            WorkflowStep step,
            StepExecutionContext execution,
            StepResult result) {
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
                : categorizer.classify(error);

        if (classification.replayable()) {
            throw new CompletionException(new WorkflowRetryableException(step.name(), classification));
        }

        publish(StatusEvent.of(
                previous.workflowId(),
                previous.workflow(),
                step.name(),
                Status.SUSPENDED,
                context.metadata().asMap(),
                classification.reason()));
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
