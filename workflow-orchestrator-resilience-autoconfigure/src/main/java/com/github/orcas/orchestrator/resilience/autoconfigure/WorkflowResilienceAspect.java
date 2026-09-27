package com.github.orcas.orchestrator.resilience.autoconfigure;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/** Applies the resilience interceptor to ordinary Spring workflow methods. */
@Aspect
public final class WorkflowResilienceAspect {
    private final WorkflowResilienceInvocationInterceptor interceptor;
    public WorkflowResilienceAspect(WorkflowResilienceInvocationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Around("@annotation(com.github.orcas.orchestrator.resilience.annotation.WorkflowCircuitBreaker)"
            + " || @within(com.github.orcas.orchestrator.resilience.annotation.WorkflowCircuitBreaker)")
    public Object invoke(ProceedingJoinPoint joinPoint) throws Throwable {
        var signature = (MethodSignature) joinPoint.getSignature();
        var method = signature.getMethod();
        var targetClass = joinPoint.getTarget() == null ? method.getDeclaringClass() : joinPoint.getTarget().getClass();
        return interceptor.invoke(targetClass, method, joinPoint::proceed);
    }
}
