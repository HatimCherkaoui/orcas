package com.github.orcas.orchestrator.autoconfigure.rest;

import com.github.orcas.orchestrator.autoconfigure.WorkflowCircuitBreakerProperties;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryScheduler;
import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.CompletionException;

/** Same generated HTTP client adapter, executed on the workflow virtual-thread executor. */
public final class AsyncRestClientWorkflowStep extends AsyncStep {
    private final RestClientWorkflowStep delegate;
    public AsyncRestClientWorkflowStep(Object client, Class<?> interfaceType, String methodName, String name,
                                       Class<? extends ContextMapper<?>> mapperType,
                                       com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer categorizer,
                                       tools.jackson.databind.ObjectMapper mapper,
                                       WorkflowRestClientProperties properties,
                                       ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
                                       ObjectProvider<WorkflowRetryScheduler> retrySchedulerProvider,
                                       ObjectProvider<WorkflowCircuitBreakerProperties> circuitBreakerPropertiesProvider) {
        this.delegate = new RestClientWorkflowStep(client, interfaceType, methodName, name,
                mapperType, categorizer, mapper, properties,
                circuitBreakerRegistryProvider, retrySchedulerProvider, circuitBreakerPropertiesProvider);
    }
    @Override public String name() { return delegate.name(); }
    @Override public StepResult executeAsync(PipelineContext context) throws Exception {
        try { return delegate.execute(context); }
        catch (Exception e) { throw new CompletionException(e); }
    }
}
