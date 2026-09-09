package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.aop.WorkflowCircuitBreakerAspect;
import com.github.orcas.orchestrator.autoconfigure.aop.WorkflowLaunchAspect;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires Resilience4j circuit breaking around workflow steps: the
 * {@link CircuitBreakerRegistry} built from {@link WorkflowCircuitBreakerProperties},
 * the {@link WorkflowLaunchAspect} that starts workflows from
 * {@code @LaunchWorkflow}-annotated controller methods, the
 * {@link WorkflowRetryScheduler} used for delayed automatic replays, and the
 * {@link WorkflowCircuitBreakerAspect} itself. Only activates when Resilience4j is on
 * the classpath.
 */
@AutoConfiguration
@org.springframework.boot.autoconfigure.AutoConfigureAfter(WorkflowCoreAutoConfiguration.class)
@ConditionalOnClass(CircuitBreakerRegistry.class)
public class WorkflowAopAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WorkflowErrorCategorizer.class)
    WorkflowErrorCategorizer workflowErrorCategorizer() { return new DefaultWorkflowErrorCategorizer(); }

    @Bean
    @ConditionalOnMissingBean(CircuitBreakerRegistry.class)
    CircuitBreakerRegistry workflowCircuitBreakerRegistry(WorkflowCircuitBreakerProperties p) {
        var config = CircuitBreakerConfig.custom()
                .slidingWindowSize(p.getSlidingWindowSize())
                .minimumNumberOfCalls(p.getMinimumNumberOfCalls())
                .failureRateThreshold(p.getFailureRateThreshold())
                .waitDurationInOpenState(p.getWaitDurationInOpenState())
                .build();
        var registry = CircuitBreakerRegistry.of(config);
        p.getInstances().forEach((name, i) -> registry.circuitBreaker(name, CircuitBreakerConfig.from(config)
                .slidingWindowSize(i.getSlidingWindowSize() == null ? p.getSlidingWindowSize() : i.getSlidingWindowSize())
                .minimumNumberOfCalls(i.getMinimumNumberOfCalls() == null ? p.getMinimumNumberOfCalls() : i.getMinimumNumberOfCalls())
                .failureRateThreshold(i.getFailureRateThreshold() == null ? p.getFailureRateThreshold() : i.getFailureRateThreshold())
                .waitDurationInOpenState(i.getWaitDurationInOpenState() == null ? p.getWaitDurationInOpenState() : i.getWaitDurationInOpenState())
                .build()));
        return registry;
    }

    @Bean
    @ConditionalOnBean(WorkflowEngine.class)
    @ConditionalOnMissingBean(WorkflowLaunchAspect.class)
    WorkflowLaunchAspect workflowLaunchAspect(WorkflowEngine engine) { return new WorkflowLaunchAspect(engine); }

    @Bean
    @ConditionalOnMissingBean(WorkflowRetryScheduler.class)
    @ConditionalOnBean(WorkflowEngine.class)
    WorkflowRetryScheduler workflowRetryScheduler(WorkflowEngine engine) { return new WorkflowRetryScheduler(engine); }

    @Bean
    @ConditionalOnMissingBean(WorkflowCircuitBreakerAspect.class)
    @ConditionalOnBean(WorkflowEngine.class)
    WorkflowCircuitBreakerAspect workflowCircuitBreakerAspect(CircuitBreakerRegistry registry, WorkflowErrorCategorizer categorizer, WorkflowRetryScheduler scheduler) {
        return new WorkflowCircuitBreakerAspect(registry, categorizer, scheduler);
    }
}
