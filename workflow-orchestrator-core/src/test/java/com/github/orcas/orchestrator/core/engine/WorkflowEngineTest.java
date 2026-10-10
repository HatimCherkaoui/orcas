package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.StatusCriteria;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void asyncRunningEventsDoNotFinishOrCountAsWorkflowCompletions() {
        var registry = new WorkflowRegistry();
        registry.register(new WorkflowDefinition("async", List.of()));
        var store = new InMemoryWorkflowStateStore();
        var context = WorkflowContext.of("input");
        store.start("async-id", "async", context);
        var completions = new java.util.concurrent.atomic.AtomicInteger();
        var telemetry = new WorkflowTelemetry() {
            @Override public boolean recordsCompletions() { return true; }
            @Override public Operation begin(String type, String name, com.github.orcas.orchestrator.core.model.Metadata metadata, java.util.Map<String,String> attributes) {
                if (name.equals("workflow.completed")) completions.incrementAndGet();
                return () -> { };
            }
        };
        var observer = new WorkflowObserver() {
            @Override public WorkflowTelemetry telemetry() { return telemetry; }
        };
        var engine = new WorkflowEngine(registry, event -> { }, store, Runnable::run,
                new com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer(), observer);
        engine.handle(StatusEvent.of("async-id", "async", "leaf", Status.RUNNING_ASYNC, context.metadata().asMap(), "running"));
        assertThat(completions.get()).isZero();
        engine.handle(StatusEvent.of("async-id", "async", "leaf", Status.SUCCESS, context.metadata().asMap(), "done"));
        assertThat(completions.get()).isEqualTo(1);
    }

    @Test
    void routesInitToFirstStepAndFirstStepToNextStep() {
        var validate = new ValidateStep();
        var reserve = new ReserveStep();
        var definition = new WorkflowDefinition("orders", List.of(
                new WorkflowDefinition.Route(StatusCriteria.init(), List.of(validate), null, null),
                new WorkflowDefinition.Route(StatusCriteria.success(validate.getClass()), List.of(reserve), null, null)));

        var registry = new WorkflowRegistry();
        registry.register(definition);
        var store = new InMemoryWorkflowStateStore();
        var events = new ArrayList<StatusEvent>();
        AtomicReference<WorkflowEngine> engine = new AtomicReference<>();
        WorkflowEventPublisher publisher = event -> {
            events.add(event);
            if (engine.get() != null) {
                engine.get().handle(event);
            }
        };
        engine.set(new WorkflowEngine(registry, publisher, store, executor));

        engine.get().start("orders", WorkflowContext.of("order-1"));

        assertThat(events).extracting(StatusEvent::step)
                .contains(StepNames.INIT, validate.name(), reserve.name());
    }

    @Test
    void startsAWorkflowAsynchronously() {
        var registry = new WorkflowRegistry();
        registry.register(new WorkflowDefinition("orders", List.of()));
        var store = new InMemoryWorkflowStateStore();
        var events = new ArrayList<StatusEvent>();
        var engine = new WorkflowEngine(registry, events::add, store, executor);

        engine.startAsync("orders", WorkflowContext.of("order-async")).join();

        assertThat(events).singleElement().extracting(StatusEvent::step)
                .isEqualTo(StepNames.INIT);
    }

    @Test
    void startsAWorkflowAndPersistsItsInitialContext() {
        var registry = new WorkflowRegistry();
        registry.register(new WorkflowDefinition("orders", List.of()));
        var store = new InMemoryWorkflowStateStore();
        var events = new ArrayList<StatusEvent>();
        var engine = new WorkflowEngine(registry, events::add, store, executor);

        engine.start("orders", WorkflowContext.of("order-7"));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.workflow()).isEqualTo("orders");
            assertThat(event.step()).isEqualTo(StepNames.INIT);
            assertThat(event.status()).isEqualTo(Status.INIT);
        });
    }

    @Test
    void publishesTerminalFailureForNonReplayableSynchronousStepErrors() {
        var failing = new FailingStep();
        var registry = new WorkflowRegistry();
        registry.register(new WorkflowDefinition("orders", List.of(
                new WorkflowDefinition.Route(StatusCriteria.init(), List.of(failing), null, null))));
        var store = new InMemoryWorkflowStateStore();
        var events = new ArrayList<StatusEvent>();
        var engine = new WorkflowEngine(registry, events::add, store, executor);
        store.start("wf-fail", "orders", WorkflowContext.of("order-1"));

        engine.handle(StatusEvent.of("wf-fail", "orders", StepNames.INIT, Status.INIT, java.util.Map.of(), "start"));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.step()).isEqualTo(failing.name());
            assertThat(event.status()).isEqualTo(Status.FAILED);
            assertThat(event.message()).contains("business validation failed");
        });
    }

    @com.github.orcas.orchestrator.core.annotation.WorkflowStep("validate")
    static final class ValidateStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }

    @com.github.orcas.orchestrator.core.annotation.WorkflowStep("reserve")
    static final class ReserveStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }

    @com.github.orcas.orchestrator.core.annotation.WorkflowStep("failing-step")
    static final class FailingStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            throw new IllegalStateException("business validation failed");
        }
    }
}
