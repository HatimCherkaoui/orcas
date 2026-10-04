package com.github.orcas.orchestrator.resilience.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.engine.WorkflowRetryStateStore;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowRetrySchedulerIntegrationTest {

  @Test
  @DisplayName("schedule records a retry attempt and replays the step after the delay")
  void scheduleRecordsRetryAndReplaysStep() {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    WorkflowRetryStateStore stateStore = mock(WorkflowRetryStateStore.class);
    var scheduler = new WorkflowRetryScheduler(engine, stateStore);

    try {
      scheduler.schedule("wf-1", "review", Duration.ZERO);

      verify(stateStore, timeout(500)).recordRetry("wf-1", "review", 0, "circuit breaker fallback replay scheduled");
      verify(engine, timeout(500)).replay("wf-1", "review");
    } finally {
      scheduler.destroy();
    }
  }

  @Test
  @DisplayName("scheduleHalfOpenReplay exposes scheduled metadata and deduplicates breaker-step pairs")
  void scheduleHalfOpenReplayDeduplicatesAndTracksScheduledReplay() {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    WorkflowRetryStateStore stateStore = mock(WorkflowRetryStateStore.class);
    CircuitBreakerRegistry registry = mock(CircuitBreakerRegistry.class);
    var scheduler = new WorkflowRetryScheduler(engine, stateStore, registry);

    try {
      scheduler.scheduleHalfOpenReplay("orders-breaker", "review", Duration.ofSeconds(5), 3);
      scheduler.scheduleHalfOpenReplay("orders-breaker", "review", Duration.ofSeconds(1), 1);

      var scheduled = scheduler.scheduledHalfOpenReplay("orders-breaker", "review");

      assertThat(scheduled).isPresent();
      assertThat(scheduled.get().breakerName()).isEqualTo("orders-breaker");
      assertThat(scheduled.get().stepName()).isEqualTo("review");
      assertThat(scheduled.get().permittedCalls()).isEqualTo(3);
      assertThat(scheduled.get().reason()).isEqualTo("circuit breaker half-open replay");
    } finally {
      scheduler.destroy();
    }
  }

  @Test
  @DisplayName("half-open replay replays probe and remaining suspended workflows when the breaker is closed")
  void halfOpenReplayReplaysProbeAndRemainingWhenBreakerCloses() {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    WorkflowRetryStateStore stateStore = mock(WorkflowRetryStateStore.class);
    CircuitBreakerRegistry registry = mock(CircuitBreakerRegistry.class);
    CircuitBreaker breaker = mock(CircuitBreaker.class);
    when(registry.circuitBreaker("orders-breaker")).thenReturn(breaker);
    when(breaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    when(stateStore.suspendedWorkflowIds("review")).thenReturn(List.of("wf-a", "wf-b", "wf-c"));
    var scheduler = new WorkflowRetryScheduler(engine, stateStore, registry);

    try {
      scheduler.scheduleHalfOpenReplay("orders-breaker", "review", Duration.ZERO, 1);

      verify(stateStore, timeout(1_000)).recordRetry("wf-a", "review", 0, "circuit breaker half-open probe");
      verify(stateStore, timeout(1_000)).recordRetry("wf-b", "review", 0, "circuit breaker closed replay");
      verify(stateStore, timeout(1_000)).recordRetry("wf-c", "review", 0, "circuit breaker closed replay");
      verify(engine, timeout(1_000)).replay("wf-a", "review");
      verify(engine, timeout(1_000)).replay("wf-b", "review");
      verify(engine, timeout(1_000)).replay("wf-c", "review");
    } finally {
      scheduler.destroy();
    }
  }

  @Test
  @DisplayName("half-open replay does nothing when no suspended workflow instances exist")
  void halfOpenReplaySkipsWhenNoSuspendedWorkflowsExist() throws Exception {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    WorkflowRetryStateStore stateStore = mock(WorkflowRetryStateStore.class);
    CircuitBreakerRegistry registry = mock(CircuitBreakerRegistry.class);
    CircuitBreaker breaker = mock(CircuitBreaker.class);
    when(registry.circuitBreaker("orders-breaker")).thenReturn(breaker);
    when(stateStore.suspendedWorkflowIds("review")).thenReturn(List.of());
    var scheduler = new WorkflowRetryScheduler(engine, stateStore, registry);

    try {
      scheduler.scheduleHalfOpenReplay("orders-breaker", "review", Duration.ZERO, 2);

      Thread.sleep(100);
      assertThat(scheduler.scheduledHalfOpenReplay("orders-breaker", "review")).isEmpty();
    } finally {
      scheduler.destroy();
    }
  }
}



