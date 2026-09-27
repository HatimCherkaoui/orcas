package com.github.orcas.orchestrator.resilience.autoconfigure;

import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;
import com.github.orcas.orchestrator.resilience.annotation.FallbackStrategy;
import com.github.orcas.orchestrator.resilience.annotation.WorkflowCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.IOException;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowResilienceInvocationInterceptorTest {
    @Test
    void derivesBreakerNameFromStepNameWhenAnnotationIsUnnamed() throws Throwable {
        var registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().build());
        var interceptor = new WorkflowResilienceInvocationInterceptor(
                registry, new DefaultWorkflowErrorCategorizer());

        var result = interceptor.invoke(TestSteps.class, TestSteps.class.getDeclaredMethod("reserve"),
                () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(registry.getAllCircuitBreakers())
                .extracting(io.github.resilience4j.circuitbreaker.CircuitBreaker::getName)
                .contains("reserve");
    }

    @Test
    void schedulesReplayForReplayFallback() throws Throwable {
        var registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().build());
        var beanFactory = new DefaultListableBeanFactory();
        var scheduler = mock(WorkflowRetryScheduler.class);
        beanFactory.registerSingleton("workflowRetryScheduler", scheduler);

        var properties = new WorkflowRetryProperties();
        properties.setDelay(Duration.ofMillis(250));
        var interceptor = new WorkflowResilienceInvocationInterceptor(
                registry,
                new DefaultWorkflowErrorCategorizer(),
                beanFactory.getBeanProvider(WorkflowRetryScheduler.class),
                properties);

        var context = WorkflowContext.of("payment");
        var execution = new StepExecutionContext(
                "wf-1", "payment-refund", "refund-payment", context, null, context.businessInput());
        WorkflowContextHolder.set("wf-1", "payment-refund", execution);
        try {
            assertThatThrownBy(() -> interceptor.invoke(
                    TestSteps.class,
                    TestSteps.class.getDeclaredMethod("replayable"),
                    () -> { throw new IOException("temporary"); }))
                    .isInstanceOf(IOException.class);

            verify(scheduler).schedule("wf-1", "replayable", Duration.ofMillis(250));
        } finally {
            WorkflowContextHolder.clear();
        }
    }

    @Test
    void suspendsWhenBreakerIsOpen() throws Throwable {
        var config = CircuitBreakerConfig.custom()
                .failureRateThreshold(1)
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .build();
        var registry = CircuitBreakerRegistry.of(config);
        var breaker = registry.circuitBreaker("reserve");
        breaker.transitionToOpenState();
        var interceptor = new WorkflowResilienceInvocationInterceptor(
                registry, new DefaultWorkflowErrorCategorizer());

        assertThatThrownBy(() -> interceptor.invoke(
                TestSteps.class, TestSteps.class.getDeclaredMethod("reserve"), () -> "never"))
                .isInstanceOf(WorkflowSuspendedException.class);
    }

    static final class TestSteps {
        @WorkflowCircuitBreaker(fallback = FallbackStrategy.REPLAY)
        String replayable() {
            return "ok";
        }

        @WorkflowCircuitBreaker
        String reserve() {
            return "ok";
        }
    }
}
