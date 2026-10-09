package com.github.orcas.orchestrator.resilience.autoconfigure;

import com.github.orcas.orchestrator.core.api.WorkflowStepInvocationInterceptor;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.engine.WorkflowRetryStateStore;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Optional Resilience4j integration for circuit breakers and replay scheduling. */
@AutoConfiguration
@ConditionalOnClass(CircuitBreakerRegistry.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({WorkflowCircuitBreakerProperties.class, WorkflowRetryProperties.class})
public final class WorkflowResilienceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CircuitBreakerRegistry workflowCircuitBreakerRegistry(WorkflowCircuitBreakerProperties properties,
                                                          WorkflowErrorCategorizer categorizer) {
        var defaults = defaultConfig(properties, categorizer);
        var registry = CircuitBreakerRegistry.of(defaults);
        properties.getInstances().forEach((name, instance) ->
                registry.circuitBreaker(name, instanceConfig(properties, instance, categorizer)));
        return registry;
    }

    private static CircuitBreakerConfig defaultConfig(WorkflowCircuitBreakerProperties properties,
                                                      WorkflowErrorCategorizer categorizer) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(error -> categorizer.classify(error).replayable())
                .build();
    }

    private static CircuitBreakerConfig instanceConfig(WorkflowCircuitBreakerProperties properties,
                                                       WorkflowCircuitBreakerProperties.Instance instance,
                                                       WorkflowErrorCategorizer categorizer) {
        var builder = CircuitBreakerConfig.from(defaultConfig(properties, categorizer));
        if (instance.getSlidingWindowSize() != null) {
            builder.slidingWindowSize(instance.getSlidingWindowSize());
        }
        if (instance.getMinimumNumberOfCalls() != null) {
            builder.minimumNumberOfCalls(instance.getMinimumNumberOfCalls());
        }
        if (instance.getFailureRateThreshold() != null) {
            builder.failureRateThreshold(instance.getFailureRateThreshold());
        }
        if (instance.getPermittedNumberOfCallsInHalfOpenState() != null) {
            builder.permittedNumberOfCallsInHalfOpenState(instance.getPermittedNumberOfCallsInHalfOpenState());
        }
        if (instance.getWaitDurationInOpenState() != null) {
            builder.waitDurationInOpenState(instance.getWaitDurationInOpenState());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowResilienceAspect workflowResilienceAspect(
            WorkflowResilienceInvocationInterceptor interceptor) {
        return new WorkflowResilienceAspect(interceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowResilienceInvocationInterceptor workflowResilienceInvocationInterceptor(
            CircuitBreakerRegistry registry,
            WorkflowErrorCategorizer categorizer,
            ObjectProvider<WorkflowRetryScheduler> retryScheduler,
            WorkflowRetryProperties retryProperties,
            WorkflowCircuitBreakerProperties circuitBreakerProperties) {
        return new WorkflowResilienceInvocationInterceptor(
                registry, categorizer, retryScheduler, retryProperties, circuitBreakerProperties);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowStepInvocationInterceptor.class)
    WorkflowStepInvocationInterceptor workflowStepInvocationInterceptor(
            WorkflowResilienceInvocationInterceptor interceptor) {
        return interceptor;
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean(WorkflowEngine.class)
    @ConditionalOnProperty(prefix = "workflow.orchestrator.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    WorkflowRetryScheduler workflowRetryScheduler(
            WorkflowEngine engine, ObjectProvider<WorkflowRetryStateStore> stateStore,
            CircuitBreakerRegistry registry, WorkflowRetryProperties properties) {
        return new WorkflowRetryScheduler(engine, stateStore.getIfAvailable(), registry, properties);
    }
}
