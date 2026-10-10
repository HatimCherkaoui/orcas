package com.github.orcas.orchestrator.kafka.autoconfigure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class WorkflowEventConsumerIntegrationTest {

  @Test
  @DisplayName("onMessage() should route valid workflow events to engine")
  void testOnMessageRouting() throws Exception {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    ObjectProvider<WorkflowEngine> provider = new ObjectProvider<>() {
      @Override
      public WorkflowEngine getObject() {
        return engine;
      }
    };
    WorkflowEventConsumer consumer = new WorkflowEventConsumer(provider, new ObjectMapper());

    StatusEvent event = StatusEvent.of(
        "wf-001",
        "orders",
        "validate-order",
        Status.RUNNING,
        java.util.Map.of(),
        "manual test");

    consumer.onMessage(new ObjectMapper().writeValueAsString(event));

    verify(engine, times(1)).handle(event);
  }

  @Test
  @DisplayName("onMessage() should handle malformed JSON without crashing")
  void testMalformedEventJson() {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    ObjectProvider<WorkflowEngine> provider = new ObjectProvider<>() {
      @Override
      public WorkflowEngine getObject() {
        return engine;
      }
    };
    WorkflowEventConsumer consumer = new WorkflowEventConsumer(provider, new ObjectMapper());

    assertThatThrownBy(() -> consumer.onMessage("{ invalid json }"))
        .isInstanceOf(Exception.class);
    verify(engine, never()).handle(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("onMessage() should propagate events for suspended workflows")
  void testSuspendedStateTransition() throws Exception {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    ObjectProvider<WorkflowEngine> provider = new ObjectProvider<>() {
      @Override
      public WorkflowEngine getObject() {
        return engine;
      }
    };
    WorkflowEventConsumer consumer = new WorkflowEventConsumer(provider, new ObjectMapper());

    StatusEvent event = StatusEvent.of(
        "wf-002",
        "orders",
        "validate-order",
        Status.SUSPENDED,
        java.util.Map.of(),
        "suspended");

    consumer.onMessage(new ObjectMapper().writeValueAsString(event));

    verify(engine, times(1)).handle(event);
  }

  @Test
  @DisplayName("onMessage() resolves the workflow engine from the provider for each message")
  void testProviderLookupOccursPerMessage() throws Exception {
    WorkflowEngine firstEngine = mock(WorkflowEngine.class);
    WorkflowEngine secondEngine = mock(WorkflowEngine.class);
    AtomicInteger calls = new AtomicInteger();
    ObjectProvider<WorkflowEngine> provider = new ObjectProvider<>() {
      @Override
      public WorkflowEngine getObject() {
        return calls.getAndIncrement() == 0 ? firstEngine : secondEngine;
      }
    };
    WorkflowEventConsumer consumer = new WorkflowEventConsumer(provider, new ObjectMapper());

    StatusEvent first = StatusEvent.of("wf-101", "orders", "validate-order", Status.RUNNING, java.util.Map.of(), "first");
    StatusEvent second = StatusEvent.of("wf-102", "orders", "capture-payment", Status.SUCCESS, java.util.Map.of(), "second");

    consumer.onMessage(new ObjectMapper().writeValueAsString(first));
    consumer.onMessage(new ObjectMapper().writeValueAsString(second));

    verify(firstEngine).handle(first);
    verify(secondEngine).handle(second);
  }

  @Test
  @DisplayName("onMessage() propagates engine failures after successful deserialization")
  void testEngineFailurePropagates() throws Exception {
    WorkflowEngine engine = mock(WorkflowEngine.class);
    ObjectProvider<WorkflowEngine> provider = new ObjectProvider<>() {
      @Override
      public WorkflowEngine getObject() {
        return engine;
      }
    };
    WorkflowEventConsumer consumer = new WorkflowEventConsumer(provider, new ObjectMapper());
    StatusEvent event = StatusEvent.of("wf-500", "orders", "validate-order", Status.RUNNING, java.util.Map.of(), "boom");
    doThrow(new IllegalStateException("engine failure")).when(engine).handle(event);

    assertThatThrownBy(() -> consumer.onMessage(new ObjectMapper().writeValueAsString(event)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("engine failure");
  }
}



