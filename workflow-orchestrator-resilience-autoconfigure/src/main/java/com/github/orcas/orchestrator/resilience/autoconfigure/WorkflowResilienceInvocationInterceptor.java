package com.github.orcas.orchestrator.resilience.autoconfigure;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStepInvocationInterceptor;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import com.github.orcas.orchestrator.resilience.annotation.WorkflowCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Optional core interceptor supplied by the resilience module. */
public final class WorkflowResilienceInvocationInterceptor implements WorkflowStepInvocationInterceptor {
    private static final ThreadLocal<Set<String>> ACTIVE_BREAKERS = ThreadLocal.withInitial(HashSet::new);
    private final CircuitBreakerRegistry registry;
    private final WorkflowErrorCategorizer categorizer;
    private final ObjectProvider<WorkflowRetryScheduler> retryScheduler;
    private final WorkflowCircuitBreakerProperties circuitBreakerProperties;

    public WorkflowResilienceInvocationInterceptor(CircuitBreakerRegistry registry,
                                                   WorkflowErrorCategorizer categorizer) {
        this(registry, categorizer, null, null, null);
    }

    public WorkflowResilienceInvocationInterceptor(
            CircuitBreakerRegistry registry,
            WorkflowErrorCategorizer categorizer,
            ObjectProvider<WorkflowRetryScheduler> retryScheduler,
            WorkflowRetryProperties retryProperties) {
        this(registry, categorizer, retryScheduler, retryProperties, null);
    }

    public WorkflowResilienceInvocationInterceptor(
            CircuitBreakerRegistry registry,
            WorkflowErrorCategorizer categorizer,
            ObjectProvider<WorkflowRetryScheduler> retryScheduler,
            WorkflowRetryProperties retryProperties,
            WorkflowCircuitBreakerProperties circuitBreakerProperties) {
        this.registry = registry;
        this.categorizer = categorizer;
        this.retryScheduler = retryScheduler;
        this.circuitBreakerProperties = circuitBreakerProperties;
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
        // Declarative REST workflow steps are also visible to the Spring AOP
        // advice. If both paths wrap the same invocation, half-open permits are
        // consumed twice and a healthy probe is rejected as CallNotPermitted.
        Set<String> active = ACTIVE_BREAKERS.get();
        if (active.contains(breakerName)) {
            return invocation.proceed();
        }
        CircuitBreaker breaker = registry.circuitBreaker(breakerName);
        active.add(breakerName);
        try {
            return breaker.executeCheckedSupplier(invocation::proceed);
        } catch (Throwable error) {
            var classification = categorizer.classify(error);
            if (breaker.getState() == CircuitBreaker.State.OPEN
                    && (classification.replayable()
                    || error instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException)) {
                classification = new com.github.orcas.orchestrator.core.error.WorkflowError(
                        com.github.orcas.orchestrator.core.error.ErrorDisposition.SUSPEND,
                        "CIRCUIT_BREAKER_OPEN", breakerName,
                        "Circuit breaker " + breakerName + " is open; waiting for its cooldown probe",
                        error);
                scheduleHalfOpenReplay(breakerName, stepName);
                throw new WorkflowSuspendedException(stepName, classification);
            }
            if (classification.disposition() == com.github.orcas.orchestrator.core.error.ErrorDisposition.FAILED) {
                throw error;
            }
            if (classification.replayable()) {
                throw new WorkflowRetryableException(stepName, classification);
            }
            throw new WorkflowSuspendedException(stepName, classification);
        } finally {
            active.remove(breakerName);
            if (active.isEmpty()) ACTIVE_BREAKERS.remove();
        }
    }

    private void scheduleHalfOpenReplay(String breakerName, String stepName) {
        if (retryScheduler == null || circuitBreakerProperties == null) {
            return;
        }
        var scheduler = retryScheduler.getIfAvailable();
        if (scheduler == null) {
            return;
        }
        scheduler.scheduleHalfOpenReplay(breakerName, stepName,
                circuitBreakerProperties.waitDurationFor(breakerName),
                circuitBreakerProperties.permittedCallsFor(breakerName));
    }
}
