package com.github.orcas.orchestrator.autoconfigure.aop;

import com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryScheduler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.concurrent.CompletionStage;

/**
 * AOP aspect implementing {@link WorkflowCircuitBreaker}: wraps the annotated
 * method/class in a Resilience4j {@link CircuitBreaker} and, when the breaker is
 * {@code OPEN}, either suspends the workflow or (if {@code fallback = "REPLAY"})
 * schedules an automatic replay via {@link WorkflowRetryScheduler}.
 */
@Aspect
public final class WorkflowCircuitBreakerAspect {
    private static final Logger log = LoggerFactory.getLogger(WorkflowCircuitBreakerAspect.class);

    private final CircuitBreakerRegistry registry;
    private final WorkflowErrorCategorizer categorizer;
    private final WorkflowRetryScheduler scheduler;

    public WorkflowCircuitBreakerAspect(CircuitBreakerRegistry registry, WorkflowErrorCategorizer categorizer, WorkflowRetryScheduler scheduler) {
        this.registry = registry; this.categorizer = categorizer; this.scheduler = scheduler;
    }

    @Around("@annotation(com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker) || @within(com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker)")
    public Object protect(ProceedingJoinPoint point) throws Throwable {
        var method = ((org.aspectj.lang.reflect.MethodSignature) point.getSignature()).getMethod();
        var stepAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, WorkflowStep.class);
        var stepName = stepAnnotation == null ? method.getName() : stepAnnotation.value();
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, WorkflowCircuitBreaker.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), WorkflowCircuitBreaker.class);
        if (annotation == null) return point.proceed();
        CircuitBreaker breaker = registry.circuitBreaker(annotation.name());
        try {
            Object result = breaker.executeCheckedSupplier(point::proceed);
            if (result instanceof CompletionStage<?> stage) {
                return stage;
            }
            return result;
        } catch (Throwable error) {
            if (breaker.getState() == CircuitBreaker.State.OPEN) {
                log.warn("Circuit breaker '{}' is OPEN; step '{}' will {}", annotation.name(), stepName,
                        "REPLAY".equalsIgnoreCase(annotation.fallback()) ? "be scheduled for automatic replay" : "suspend the workflow");
                if ("REPLAY".equalsIgnoreCase(annotation.fallback())) {
                    var execution = WorkflowContextHolder.execution();
                    if (execution != null && execution.workflowId() != null) scheduler.schedule(execution.workflowId(), stepName, java.time.Duration.ofSeconds(annotation.retryDelaySeconds()));
                }
                throw new WorkflowSuspendedException(stepName, categorizer.classify(error));
            }
            throw error;
        }
    }

    private Throwable unwrap(Throwable t) {
        return t.getCause() == null ? t : t.getCause();
    }
}
