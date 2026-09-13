package com.github.orcas.orchestrator.core.engine;


import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.error.WorkflowError;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.*;
import com.github.orcas.orchestrator.core.retry.WorkflowCriteriaNotMatchedException;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowEngineTest {

    private WorkflowRegistry registry;
    private WorkflowEventPublisher publisher;
    private WorkflowStateStore store;
    private Executor executor;
    private WorkflowErrorCategorizer categorizer;

    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        registry = mock(WorkflowRegistry.class);
        publisher = mock(WorkflowEventPublisher.class);
        store = mock(WorkflowStateStore.class);
        executor = Runnable::run;
        categorizer = mock(WorkflowErrorCategorizer.class);

        engine = new WorkflowEngine(
                registry,
                publisher,
                store,
                executor,
                categorizer
        );
    }

    @Test
    void shouldCreateEngineWithDefaultCategorizer() {
        var defaultEngine = new WorkflowEngine(
                registry,
                publisher,
                store,
                executor
        );

        assertThat(defaultEngine)
                .isNotNull();
    }

    @Test
    void shouldStartWorkflow() {
        // Given
        var workflowName = "myWorkflow";
        var context = mockPipelineContext();

        var definition = mock(WorkflowDefinition.class);

        when(registry.get(workflowName))
                .thenReturn(definition);

        // When
        engine.start(workflowName, context);

        // Then
        var idCaptor = ArgumentCaptor.forClass(String.class);

        verify(store).start(
                idCaptor.capture(),
                eq(workflowName),
                eq(context)
        );

        assertThat(idCaptor.getValue())
                .isNotBlank();

        verify(publisher).publish(any(StatusEvent.class));
    }

    @Test
    void shouldRejectUnknownWorkflowWhenStarting() {
        // Given
        var context = mockPipelineContext();

        when(registry.get("unknown"))
                .thenReturn(null);

        // When / Then
        assertThatThrownBy(() ->
                engine.start("unknown", context)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown workflow: unknown");

        verifyNoInteractions(store);
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldRemoveMdcValuesAfterSuccessfulStart() {
        // Given
        var context = mockPipelineContext();

        when(registry.get("myWorkflow"))
                .thenReturn(mock(WorkflowDefinition.class));

        // When
        engine.start("myWorkflow", context);

        // Then
        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW_ID
        )).isNull();

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW
        )).isNull();
    }

    @Test
    void shouldRemoveMdcValuesWhenStartFails() {
        // Given
        var context = mockPipelineContext();

        when(registry.get("myWorkflow"))
                .thenReturn(mock(WorkflowDefinition.class));

        doThrow(new IllegalStateException("database unavailable"))
                .when(store)
                .start(any(), eq("myWorkflow"), eq(context));

        // When / Then
        assertThatThrownBy(() ->
                engine.start("myWorkflow", context)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW_ID
        )).isNull();

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW
        )).isNull();
    }

    @Test
    void shouldStartWorkflowFromHttpPost() {
        // Given
        var body = Map.of(
                "orderId", "123",
                "amount", 100
        );

        var headers = Map.of(
                "X-Correlation-Id", "abc-123",
                "X-Source", "test"
        );

        when(registry.get("myWorkflow"))
                .thenReturn(mock(WorkflowDefinition.class));

        // When
        engine.startFromHttpPost(
                "myWorkflow",
                body,
                headers
        );

        // Then
        verify(store).start(
                any(String.class),
                eq("myWorkflow"),
                any(PipelineContext.class)
        );

        verify(publisher).publish(any(StatusEvent.class));
    }

    @Test
    void shouldReplayExistingStep() throws Exception {
        // Given
        var workflowId = "workflow-123";
        var workflowName = "myWorkflow";
        var stepName = "validate-order";

        var context = mockPipelineContext();

        var definition = mock(WorkflowDefinition.class);
        var step = mock(Step.class);
        var result = mock(StepResult.class);

        when(store.context(workflowId))
                .thenReturn(context);

        when(store.workflowName(workflowId))
                .thenReturn(workflowName);

        when(registry.get(workflowName))
                .thenReturn(definition);

        when(definition.findStep(stepName))
                .thenReturn(step);

        when(step.name())
                .thenReturn(stepName);

//        when(store.stepContext(workflowId, stepName))
//                .thenReturn(null);
        when(store.stepContext(workflowId, stepName))
                .thenReturn(mock(StepContext.class));

        when(result.context())
                .thenReturn(context);

        when(step.execute(context))
                .thenReturn(result);
        // When
        engine.replay(workflowId, stepName);

        // Then
        verify(store, times(2)).context(workflowId);
        verify(store).workflowName(workflowId);
        verify(definition).findStep(stepName);
    }

    @Test
    void shouldRejectReplayForUnknownStep() {
        // Given
        var workflowId = "workflow-123";
        var workflowName = "myWorkflow";

        var context = mockPipelineContext();

        var definition = mock(WorkflowDefinition.class);

        when(store.context(workflowId))
                .thenReturn(context);

        when(store.workflowName(workflowId))
                .thenReturn(workflowName);

        when(registry.get(workflowName))
                .thenReturn(definition);

        when(definition.findStep("unknown-step"))
                .thenReturn(null);

        // When / Then
        assertThatThrownBy(() ->
                engine.replay(workflowId, "unknown-step")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown step: unknown-step");
    }

    @Test
    void shouldRejectEventForUnknownWorkflow() {
        // Given
        var event = event(
                "workflow-123",
                "unknown-workflow",
                "step-1",
                Status.SUCCESS
        );

        when(registry.get("unknown-workflow"))
                .thenReturn(null);

        // When / Then
        assertThatThrownBy(() ->
                engine.handle(event)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown workflow: unknown-workflow");

        verify(store, never()).record(any(), any());
    }

    @Test
    void shouldRecordIncomingEvent() {
        // Given
        var workflowName = "myWorkflow";
        var stepName = "validate-order";

        var definition = mock(WorkflowDefinition.class);
        var step = mock(WorkflowStep.class);

        when(registry.get(workflowName))
                .thenReturn(definition);

        when(definition.findStep(stepName))
                .thenReturn(step);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(false);

        when(step.name())
                .thenReturn(stepName);

        var event = event(
                "workflow-123",
                workflowName,
                stepName,
                Status.RUNNING
        );

        // When
        engine.handle(event);

        // Then
        verify(store).record(
                eq(event),
                eq(step.getClass().getName())
        );
    }

    @Test
    void shouldFinishWorkflowWhenNoRoutesAndTerminalStatus() {
        // Given
        var workflowName = "myWorkflow";
        var stepName = "finish";

        var definition = mock(WorkflowDefinition.class);
        var step = mock(WorkflowStep.class);

        when(registry.get(workflowName))
                .thenReturn(definition);

        when(definition.findStep(stepName))
                .thenReturn(step);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(false);

        var event = event(
                "workflow-123",
                workflowName,
                stepName,
                Status.SUCCESS
        );

        // When
        engine.handle(event);

        // Then
        verify(store).finish(event);
    }

    @Test
    void shouldNotFinishWorkflowWhenStatusIsRunning() {
        // Given
        var definition = mock(WorkflowDefinition.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("step-1"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(false);

        var event = event(
                "workflow-123",
                "myWorkflow",
                "step-1",
                Status.RUNNING
        );

        // When
        engine.handle(event);

        // Then
        verify(store, never()).finish(any());
    }

    @Test
    void shouldNotFinishWorkflowWhenStatusIsSuspended() {
        // Given
        var definition = mock(WorkflowDefinition.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("step-1"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(false);

        var event = event(
                "workflow-123",
                "myWorkflow",
                "step-1",
                Status.SUSPENDED
        );

        // When
        engine.handle(event);

        // Then
        verify(store, never()).finish(any());
    }

    @Test
    void shouldThrowWhenStepIsWaitingForJoin() {
        // Given
        var definition = mock(WorkflowDefinition.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("step-1"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(true);

        var event = event(
                "workflow-123",
                "myWorkflow",
                "step-1",
                Status.SUCCESS
        );

        // When / Then
        assertThatThrownBy(() ->
                engine.handle(event)
        )
                .isInstanceOf(
                        WorkflowCriteriaNotMatchedException.class
                );

        verify(store, never()).finish(any());
    }

    @Test
    void shouldClearMdcAfterHandlingEvent() {
        // Given
        var definition = mock(WorkflowDefinition.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("step-1"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(false);

        var event = event(
                "workflow-123",
                "myWorkflow",
                "step-1",
                Status.SUCCESS
        );

        // When
        engine.handle(event);

        // Then
        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW_ID
        )).isNull();

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW
        )).isNull();

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_STEP
        )).isNull();
    }

    @Test
    void shouldClearMdcWhenHandlingEventFails() {
        // Given
        when(registry.get("myWorkflow"))
                .thenThrow(new IllegalStateException("registry failure"));

        var event = event(
                "workflow-123",
                "myWorkflow",
                "step-1",
                Status.SUCCESS
        );

        // When / Then
        assertThatThrownBy(() ->
                engine.handle(event)
        )
                .isInstanceOf(IllegalStateException.class);

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW_ID
        )).isNull();

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_WORKFLOW
        )).isNull();

        assertThat(org.slf4j.MDC.get(
                WorkflowContextHolder.MDC_STEP
        )).isNull();
    }

    @Test
    void shouldExecuteMatchingRoute() throws Exception {
        // Given
        var workflowName = "myWorkflow";
        var stepName = "validate-order";

        var definition = mock(WorkflowDefinition.class);
        var step = mock(Step.class);
        var route = mock(WorkflowDefinition.Route.class);

        var context = mockPipelineContext();
        var stepContext = mock(StepContext.class);
        var resultContext = mockPipelineContext();
        var result = mock(StepResult.class);

        when(registry.get(workflowName))
                .thenReturn(definition);

        when(definition.findStep("start"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of(route));

        when(route.joinStep())
                .thenReturn(null);

        when(route.steps())
                .thenReturn(List.of(step));

        when(step.name())
                .thenReturn(stepName);

        when(store.context("workflow-123"))
                .thenReturn(context);

        when(store.stepContext("workflow-123", "start"))
                .thenReturn(stepContext);

        when(result.context())
                .thenReturn(resultContext);

        when(result.status())
                .thenReturn(Status.SUCCESS);

        when(result.message())
                .thenReturn("completed");

        when(resultContext.businessInput())
                .thenReturn("output");

        when(resultContext.metadata())
                .thenReturn(mockMetadata());

        when(context.businessInput())
                .thenReturn("input");

        when(step.execute(context))
                .thenReturn(result);

        // When
        engine.handle(event(
                "workflow-123",
                workflowName,
                "start",
                Status.SUCCESS
        ));

        // Then
        verify(step).execute(context);
        verify(store).saveStepContext(any(StepContext.class));
        verify(store).updateContext(
                "workflow-123",
                resultContext
        );

        verify(publisher).publish(any(StatusEvent.class));
    }

    @Test
    void shouldConvertFailedStepResultToWorkflowRetryableException() throws Exception {
        // Given
        var definition = mock(WorkflowDefinition.class);
        var step = mock(Step.class);
        var route = mock(WorkflowDefinition.Route.class);

        var context = mockPipelineContext();
        var stepContext = mock(StepContext.class);
        var result = mock(StepResult.class);
        var resultContext = mockPipelineContext();

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("start"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of(route));

        when(route.joinStep())
                .thenReturn(null);

        when(route.steps())
                .thenReturn(List.of(step));

        when(step.name())
                .thenReturn("validate-order");

        when(store.context("workflow-123"))
                .thenReturn(context);

        when(store.stepContext("workflow-123", "start"))
                .thenReturn(stepContext);

        when(step.execute(context))
                .thenReturn(result);

        when(result.context())
                .thenReturn(resultContext);

        when(result.status())
                .thenReturn(Status.FAILED);

        when(result.message())
                .thenReturn("validation failed");

        when(resultContext.businessInput())
                .thenReturn("output");

        when(resultContext.metadata())
                .thenReturn(mockMetadata());

        // When / Then
        assertThatThrownBy(() ->
                engine.handle(event(
                        "workflow-123",
                        "myWorkflow",
                        "start",
                        Status.SUCCESS
                ))
        )
                .isInstanceOf(WorkflowRetryableException.class)
                .hasMessageContaining("Step returned FAILED");

        verify(step).execute(context);
        verify(store).saveStepContext(any(StepContext.class));
        verify(store).updateContext(
                "workflow-123",
                resultContext
        );
    }

    @Test
    void shouldConvertUnexpectedStepExceptionToRetryableException() throws Exception {
        // Given
        var definition = mock(WorkflowDefinition.class);
        var step = mock(Step.class);
        var route = mock(WorkflowDefinition.Route.class);

        var context = mockPipelineContext();
        var stepContext = mock(StepContext.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("start"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of(route));

        when(route.joinStep())
                .thenReturn(null);

        when(route.steps())
                .thenReturn(List.of(step));

        when(step.name())
                .thenReturn("validate-order");

        when(store.context("workflow-123"))
                .thenReturn(context);

        when(store.stepContext("workflow-123", "start"))
                .thenReturn(stepContext);

        var originalException =
                new IllegalStateException("temporary failure");

        when(step.execute(context))
                .thenThrow(originalException);

        var classification = mock(WorkflowError.class);

        when(classification.replayable())
                .thenReturn(true);

        when(categorizer.classify(originalException))
                .thenReturn(classification);

        // When / Then
        assertThatThrownBy(() ->
                engine.handle(event(
                        "workflow-123",
                        "myWorkflow",
                        "start",
                        Status.SUCCESS
                ))
        )
                .isInstanceOf(WorkflowRetryableException.class);

        verify(categorizer)
                .classify(originalException);

        verify(store)
                .saveStepContext(any(StepContext.class));
    }

    @Test
    void shouldPublishRunningAsyncEventForAsyncStep() throws Exception {
        // This test intentionally uses the real executor Runnable::run,
        // making CompletableFuture execution deterministic.

        // Given
        var definition = mock(WorkflowDefinition.class);
        var route = mock(WorkflowDefinition.Route.class);
        var step = mock(AsyncStep.class);

        var context = mockPipelineContext();
        var stepContext = mock(StepContext.class);
        var resultContext = mockPipelineContext();
        var result = mock(StepResult.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("start"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of(route));

        when(route.joinStep())
                .thenReturn(null);

        when(route.steps())
                .thenReturn(List.of(step));

        when(step.name())
                .thenReturn("async-step");

        when(store.context("workflow-123"))
                .thenReturn(context);

        when(store.stepContext("workflow-123", "start"))
                .thenReturn(stepContext);

        when(step.executeAsync(context))
                .thenReturn(result);

        when(result.context())
                .thenReturn(resultContext);

        when(result.status())
                .thenReturn(Status.SUCCESS);

        when(result.message())
                .thenReturn("async completed");

        when(resultContext.businessInput())
                .thenReturn("async-output");

        when(resultContext.metadata())
                .thenReturn(mockMetadata());

        // When
        engine.handle(event(
                "workflow-123",
                "myWorkflow",
                "start",
                Status.SUCCESS
        ));

        // Then
        verify(publisher).publish(argThat(statusEvent ->
                statusEvent.status() == Status.RUNNING_ASYNC
                        && statusEvent.step().equals("async-step")
        ));

        verify(step).executeAsync(context);

        verify(store)
                .updateContext(
                        "workflow-123",
                        resultContext
                );
    }

    @Test
    void shouldDoNothingWhenPostProcessingIsNotApplicable() {
        // This simply verifies that normal mocked dependencies do not
        // introduce unexpected behavior in a terminal event.

        var definition = mock(WorkflowDefinition.class);

        when(registry.get("myWorkflow"))
                .thenReturn(definition);

        when(definition.findStep("finish"))
                .thenReturn(null);

        when(definition.matching(any(StatusEvent.class)))
                .thenReturn(List.of());

        when(definition.waitingFor(any(StatusEvent.class)))
                .thenReturn(false);

        assertThatCode(() ->
                engine.handle(event(
                        "workflow-123",
                        "myWorkflow",
                        "finish",
                        Status.SUCCESS
                ))
        ).doesNotThrowAnyException();

        verify(store).finish(any(StatusEvent.class));
    }

    private static StatusEvent event(
            String workflowId,
            String workflow,
            String step,
            Status status
    ) {
        return new StatusEvent(
                workflowId,
                workflow,
                step,
                status,
                Map.of(),
                "test",
                java.time.Instant.now()
        );
    }

    private static PipelineContext mockPipelineContext() {
        var context = mock(PipelineContext.class);

        when(context.businessInput())
                .thenReturn("input");

        when(context.metadata())
                .thenReturn(mockMetadata());

        return context;
    }

    private static Metadata mockMetadata() {
        var metadata = mock(Metadata.class);

        //when(metadata.asMap())
        //        .thenReturn(Map.of("key", "value"));

        return metadata;
    }
}