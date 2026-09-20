package com.github.orcas.orchestrator.autoconfigure.aop;

import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryScheduler;
import com.github.orcas.orchestrator.autoconfigure.WorkflowCircuitBreakerProperties;
import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClient;
import com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.retry.WorkflowSuspendedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.concurrent.CompletionStage;

import static com.github.orcas.orchestrator.core.annotation.FallbackStrategy.REPLAY;

/**
 * AOP aspect implementing {@link WorkflowCircuitBreaker}: wraps the annotated
 * method/class in a Resilience4j {@link CircuitBreaker} and, when the breaker is
 * {@code OPEN}, suspends the workflow and, if {@code fallback = "REPLAY"}, schedules
 * a half-open replay batch via {@link WorkflowRetryScheduler}.
 */
@Aspect
public final class WorkflowCircuitBreakerAspect {
    private static final Logger log = LoggerFactory.getLogger(WorkflowCircuitBreakerAspect.class);

    private final CircuitBreakerRegistry registry;
    private final WorkflowErrorCategorizer categorizer;
    private final WorkflowCircuitBreakerProperties properties;
    private final WorkflowRetryScheduler scheduler;

    public WorkflowCircuitBreakerAspect(CircuitBreakerRegistry registry, WorkflowErrorCategorizer categorizer,
                                        WorkflowCircuitBreakerProperties properties, WorkflowRetryScheduler scheduler) {
        this.registry = registry;
        this.categorizer = categorizer;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Around("@annotation(com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker) || @within(com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker)")
    public Object protect(ProceedingJoinPoint point) throws Throwable {
        var method = ((org.aspectj.lang.reflect.MethodSignature) point.getSignature()).getMethod();
        if (AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), WorkflowRestClient.class) != null) {
            return point.proceed();
        }
        var stepAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, WorkflowStep.class);
        var stepName = stepAnnotation == null ? method.getName() : stepAnnotation.value();
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, WorkflowCircuitBreaker.class);
        if (annotation == null)
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), WorkflowCircuitBreaker.class);
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
                        REPLAY.name().equalsIgnoreCase(annotation.fallback().name()) ? "be scheduled for half-open replay" : "suspend the workflow");
                if (REPLAY.name().equalsIgnoreCase(annotation.fallback().name())) {
                    var breakerInstance = properties.getInstances().get(annotation.name());
                    var waitDuration = breakerInstance != null && breakerInstance.getWaitDurationInOpenState() != null
                            ? breakerInstance.getWaitDurationInOpenState()
                            : properties.getWaitDurationInOpenState();
                    var permittedCalls = breakerInstance != null && breakerInstance.getPermittedNumberOfCallsInHalfOpenState() != null
                            ? breakerInstance.getPermittedNumberOfCallsInHalfOpenState()
                            : properties.getPermittedNumberOfCallsInHalfOpenState();
                    scheduler.scheduleHalfOpenReplay(annotation.name(), stepName,
                            waitDuration,
                            permittedCalls);
                }
                throw new WorkflowSuspendedException(stepName, categorizer.classify(error));
            }
            throw error;
        }
    }
}
