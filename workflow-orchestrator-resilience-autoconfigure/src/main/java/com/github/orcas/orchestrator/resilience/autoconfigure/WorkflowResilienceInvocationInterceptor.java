package com.github.orcas.orchestrator.resilience.autoconfigure;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStepInvocationInterceptor;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;
import com.github.orcas.orchestrator.resilience.annotation.FallbackStrategy;
import com.github.orcas.orchestrator.resilience.annotation.WorkflowCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/** Optional core interceptor supplied by the resilience module. */
public final class WorkflowResilienceInvocationInterceptor implements WorkflowStepInvocationInterceptor {
    private final CircuitBreakerRegistry registry;
    private final WorkflowErrorCategorizer categorizer;
    private final ObjectProvider<WorkflowRetryScheduler> retryScheduler;
    private final WorkflowRetryProperties retryProperties;

    public WorkflowResilienceInvocationInterceptor(CircuitBreakerRegistry registry,
                                                   WorkflowErrorCategorizer categorizer) {
        this(registry, categorizer, null, null);
    }

    public WorkflowResilienceInvocationInterceptor(
            CircuitBreakerRegistry registry,
            WorkflowErrorCategorizer categorizer,
            ObjectProvider<WorkflowRetryScheduler> retryScheduler,
            WorkflowRetryProperties retryProperties) {
        this.registry = registry;
        this.categorizer = categorizer;
        this.retryScheduler = retryScheduler;
        this.retryProperties = retryProperties;
    }

    @Override
    public Object invoke(Class<?> owner, Method method, Invocation invocation) throws Throwable {
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, WorkflowCircuitBreaker.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(owner, WorkflowCircuitBreaker.class);
        }
        if (annotation == null) {
            return invocation.proceed();
        }

        String stepName = StepNames.of(method);
        String breakerName = annotation.name().isBlank() ? stepName : annotation.name();
        CircuitBreaker breaker = registry.circuitBreaker(breakerName);
        try {
            return breaker.executeCheckedSupplier(invocation::proceed);
        } catch (Throwable error) {
            if (annotation.fallback() == FallbackStrategy.REPLAY) {
                scheduleReplay(stepName);
            }
            if (breaker.getState() == CircuitBreaker.State.OPEN || annotation.fallback() == FallbackStrategy.SUSPEND) {
                throw new WorkflowSuspendedException(stepName, categorizer.classify(error));
            }
            if (error instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(error);
        }
    }

    private void scheduleReplay(String stepName) {
        if (retryScheduler == null || retryProperties == null) {
            return;
        }
        var execution = WorkflowContextHolder.execution();
        if (execution == null || execution.workflowId() == null) {
            return;
        }
        var scheduler = retryScheduler.getIfAvailable();
        if (scheduler == null) {
            return;
        }
        var stepRetry = retryProperties.forStep(stepName);
        var delay = stepRetry == null || stepRetry.getDelay() == null
                ? retryProperties.getDelay()
                : stepRetry.getDelay();
        scheduler.schedule(execution.workflowId(), stepName, delay);
    }
}
