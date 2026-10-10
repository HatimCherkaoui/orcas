package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.core.WorkflowBeanResolver;
import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.WorkflowStepInvocationInterceptor;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;

/** Runs a declarative REST method through the workflow async executor. */
public final class AsyncRestClientWorkflowStep extends AsyncStep {
    private final RestClientWorkflowStep delegate;

    public AsyncRestClientWorkflowStep(
            Object client,
            Method method,
            Class<? extends ContextMapper<?>> mapperType,
            Class<? extends ResponseConsumer<?>> consumerType,
            WorkflowErrorCategorizer categorizer,
            ObjectMapper mapper,
            WorkflowRestClientProperties properties,
            WorkflowBeanResolver resolver) {
        this.delegate = new RestClientWorkflowStep(
                client, method, mapperType, consumerType, categorizer, mapper, properties, resolver);
    }

    @Autowired(required = false)
    void setInvocationInterceptor(WorkflowStepInvocationInterceptor invocationInterceptor) {
        delegate.setInvocationInterceptor(invocationInterceptor);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public StepResult executeAsync(WorkflowContext context) throws Exception {
        return delegate.execute(context);
    }
}
