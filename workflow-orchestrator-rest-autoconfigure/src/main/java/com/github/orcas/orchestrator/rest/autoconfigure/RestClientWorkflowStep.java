package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.core.WorkflowBeanResolver;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.api.WorkflowStepInvocationInterceptor;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.Objects;

/** Executes one {@link WorkflowRestCall} method through its generated client proxy. */
public class RestClientWorkflowStep extends Step {
    private final Object client;
    private final Method method;
    private final ContextMapper<Object> requestMapper;
    private final RestClientResponseHandler responseHandler;
    private final RestClientResponseCache cache;
    private WorkflowStepInvocationInterceptor invocationInterceptor = (owner, method, invocation) -> invocation.proceed();

    @Autowired(required = false)
    void setInvocationInterceptor(WorkflowStepInvocationInterceptor invocationInterceptor) {
        this.invocationInterceptor = invocationInterceptor;
    }

    /** Creates a REST workflow step with Spring-resolved mapper and response consumer. */
    @SuppressWarnings("unchecked")
    public RestClientWorkflowStep(
            Object client,
            Method method,
            Class<? extends ContextMapper<?>> mapperType,
            Class<? extends ResponseConsumer<?>> responseConsumerType,
            WorkflowErrorCategorizer categorizer,
            ObjectMapper objectMapper,
            WorkflowRestClientProperties properties,
            WorkflowBeanResolver resolver) {
        this.client = Objects.requireNonNull(client, "client");
        this.method = Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resolver, "resolver");
        this.requestMapper = (ContextMapper<Object>) resolver.mapper(mapperType);
        var responseConsumer = (ResponseConsumer<Object>) resolver.consumer(responseConsumerType);
        this.responseHandler = new RestClientResponseHandler(method, categorizer, responseConsumer);
        this.cache = new RestClientResponseCache(properties, objectMapper);
    }

    @Override
    public String name() {
        return responseStepName();
    }

    @Override
    public StepResult execute(WorkflowContext context) throws Exception {
        StepExecutionContext execution = WorkflowContextHolder.step();
        Object mapped = requestMapper.map(execution);
        Object[] arguments = RestClientArguments.resolve(method, mapped, execution);

        var cached = cache.get(method, arguments);
        if (cached.isPresent()) {
            execution.output(cached.get());
            return StepResult.success(context);
        }

        return invoke(arguments, context, execution);
    }

    private StepResult invoke(Object[] arguments, WorkflowContext context, StepExecutionContext execution)
            throws Exception {
        try {
            return (StepResult) invocationInterceptor.invoke(
                    method.getDeclaringClass(),
                    method,
                    () -> {
                        Object response;
                        try {
                            response = resolve(method.invoke(client, arguments));
                        } catch (ReflectiveOperationException exception) {
                            if (exception.getCause() != null) {
                                throw exception.getCause();
                            }
                            throw exception;
                        }
                        StepResult result = responseHandler.handle(context, execution, response);
                        if (isCacheable(response)) {
                            cache.put(method, arguments, response);
                        }
                        return result;
                    });
        } catch (ReflectiveOperationException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        } catch (Exception exception) {
            throw exception;
        } catch (Throwable throwable) {
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("REST workflow invocation failed", throwable);
        }
    }

    private static Object resolve(Object response) {
        if (response instanceof Mono<?> mono) {
            return mono.block();
        }
        if (response instanceof Flux<?> flux) {
            return flux.collectList().block();
        }
        return response;
    }

    private String responseStepName() {
        return com.github.orcas.orchestrator.core.api.StepNames.of(method);
    }

    private static boolean isCacheable(Object response) {
        return !(response instanceof ResponseEntity<?>)
                && !containsResponseEntity(response);
    }

    private static boolean containsResponseEntity(Object response) {
        return response instanceof java.util.List<?> list
                && !list.isEmpty()
                && list.stream().allMatch(ResponseEntity.class::isInstance);
    }
}
