package com.github.orcas.orchestrator.autoconfigure.rest;

import com.github.orcas.orchestrator.autoconfigure.WorkflowCircuitBreakerProperties;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryScheduler;
import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Same generated HTTP client adapter, executed on the workflow virtual-thread executor.
 */
public final class AsyncRestClientWorkflowStep extends AsyncStep {
    private final RestClientWorkflowStep delegate;

    public AsyncRestClientWorkflowStep(Object client, Class<?> interfaceType, String methodName, String name,
                                       Class<? extends ContextMapper<?>> mapperType,
                                       Class<? extends ResponseConsumer<?>> responseConsumerType,
                                       WorkflowErrorCategorizer categorizer,
                                       ObjectMapper mapper,
                                       WorkflowRestClientProperties properties,
                                       ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
                                       ObjectProvider<WorkflowRetryScheduler> retrySchedulerProvider,
                                       ObjectProvider<WorkflowCircuitBreakerProperties> circuitBreakerPropertiesProvider,
                                       ApplicationContext applicationContext) {
        this.delegate = new RestClientWorkflowStep(
                client,
                interfaceType,
                methodName,
                name,
                mapperType,
                responseConsumerType,
                categorizer,
                mapper,
                properties,
                circuitBreakerRegistryProvider,
                retrySchedulerProvider,
                circuitBreakerPropertiesProvider,
                applicationContext);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public StepResult executeAsync(PipelineContext context) throws Exception {
        return delegate.execute(context);
    }
}
