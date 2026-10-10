package com.github.orcas.orchestrator.resilience.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowResilienceInvocationInterceptorTest {

  @Test
  @DisplayName("interceptor class should be loadable")
  void classLoads() throws Exception {
    assertThat(Class.forName("com.github.orcas.orchestrator.resilience.autoconfigure.WorkflowResilienceInvocationInterceptor"))
        .isNotNull();
  }
}
