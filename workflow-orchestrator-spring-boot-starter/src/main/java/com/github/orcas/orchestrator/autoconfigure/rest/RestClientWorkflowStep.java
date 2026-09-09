package com.github.orcas.orchestrator.autoconfigure.rest;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.retry.WorkflowResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Step} that invokes a single method of a {@link WorkflowRestClient}
 * declarative interface, mapping the current {@link StepExecutionContext} to call
 * arguments and the HTTP response back onto the {@link PipelineContext}. Non-2xx
 * responses are classified via {@link WorkflowErrorCategorizer#classifyResponse(int)}
 * and surfaced as a {@link WorkflowResponseException}, which the engine treats as
 * replayable or terminal accordingly. Supports an optional in-memory response cache
 * (see {@link WorkflowRestClientProperties.Cache}).
 */
public final class RestClientWorkflowStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(RestClientWorkflowStep.class);

    private final Object client;
    private final Method method;
    private final String name;
    private final WorkflowErrorCategorizer categorizer;
    private final ObjectMapper mapper;
    private final WorkflowRestClientProperties properties;
    private final ContextMapper<Object> requestMapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Object value, Instant expiresAt) {
    }

    public RestClientWorkflowStep(Object client, Class<?> interfaceType, String methodName, String name,
                                  Class<? extends ContextMapper<?>> mapperType,
                                  WorkflowErrorCategorizer categorizer, ObjectMapper mapper,
                                  WorkflowRestClientProperties properties) {
        this.client = client;
        this.name = name;
        this.categorizer = categorizer;
        this.mapper = mapper;
        this.properties = properties;
        try {
            @SuppressWarnings("unchecked")
            ContextMapper<Object> resolved = (ContextMapper<Object>) mapperType.getDeclaredConstructor().newInstance();
            this.requestMapper = resolved;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("REST mapper must expose a public no-arg constructor: " + mapperType.getName(), e);
        }
        this.method = find(interfaceType, methodName);
    }

    private Method find(Class<?> type, String methodName) {
        return java.util.Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("REST client method not found: " + type.getName() + "#" + methodName));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        StepExecutionContext execution = WorkflowContextHolder.step();
        Object[] args = arguments(execution);
        String cacheKey = null;
        if (properties.getCache().isEnabled() && !ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
            cacheKey = method.toGenericString() + "|" + safeJson(args);
            var cached = cache.get(cacheKey);
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
                log.debug("Serving cached response for step '{}'", name);
                execution.output(cached.value());
                return StepResult.success(context);
            }
            if (cached != null) cache.remove(cacheKey);
        }
        Object response;
        try {
            log.debug("Invoking REST client method {} for step '{}'", method.getName(), name);
            response = method.invoke(client, args);
            if (response instanceof Mono<?> mono) response = mono.block();
            else if (response instanceof Flux<?> flux) response = flux.collectList().block();
        } catch (java.lang.reflect.InvocationTargetException e) {
            log.warn("REST client call failed for step '{}': {}", name, e.getCause() != null ? e.getCause().toString() : e.toString());
            throw unwrap(e.getCause());
        }
        if (cacheKey != null && !(response instanceof ResponseEntity<?>)) {
            if (cache.size() >= properties.getCache().getMaxEntries())
                cache.keySet().stream().findFirst().ifPresent(cache::remove);
            cache.put(cacheKey, new Cached(response, Instant.now().plus(properties.getCache().getTtl())));
        }
        if (response instanceof ResponseEntity<?> entity) {
            return response(context, entity);
        }
        if (response instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ResponseEntity<?>) {
            var bodies = new ArrayList<>();
            for (Object item : list) bodies.add(((ResponseEntity<?>) item).getBody());
            ResponseEntity<?> last = (ResponseEntity<?>) list.get(list.size() - 1);
            return response(context, new ResponseEntity<>(bodies, last.getHeaders(), last.getStatusCode()));
        }
        if (execution != null) execution.output(response);
        return StepResult.success(context);
    }

    private StepResult response(PipelineContext context, ResponseEntity<?> entity) {
        context.metadata().put("http.response.status", Integer.toString(entity.getStatusCode().value()));
        entity.getHeaders().forEach((key, values) -> context.metadata().put("http.response.header." + key, String.join(",", values)));
        StepExecutionContext execution = WorkflowContextHolder.step();
        if (execution != null) {
            execution.attribute("http.status", entity.getStatusCode().value());
            entity.getHeaders().forEach((key, values) -> execution.attribute("http.header." + key, String.join(",", values)));
        }
        if (entity.getStatusCode().isError())
            throw new WorkflowResponseException(name, entity.getStatusCode().value(), categorizer.classifyResponse(entity.getStatusCode().value()));
        if (execution != null) execution.output(entity.getBody());
        return StepResult.success(context);
    }

    private String safeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return java.util.Arrays.deepToString(new Object[]{value});
        }
    }

    private Object[] arguments(StepExecutionContext execution) throws Exception {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) return new Object[0];
        Object mapped = requestMapper.map(execution);
        if (parameters.length == 1) {
            Parameter parameter = parameters[0];
            if (parameter.getAnnotation(RequestBody.class) != null) return new Object[]{mapped};
            if (parameter.getAnnotation(PathVariable.class) != null) {
                Object value = mapped instanceof Map<?, ?> map
                        ? map.get(parameter.getAnnotation(PathVariable.class).value())
                        : mapped;
                return new Object[]{value != null ? value : resolveValue(parameter.getAnnotation(PathVariable.class).value(), execution)};
            }
            if (parameter.getAnnotation(RequestHeader.class) != null)
                return new Object[]{execution.workflowContext().metadata().get(parameter.getAnnotation(RequestHeader.class).value())};
            return new Object[]{mapped};
        }
        List<Object> args = new ArrayList<>(parameters.length);
        for (Parameter parameter : parameters) {
            PathVariable path = parameter.getAnnotation(PathVariable.class);
            RequestHeader header = parameter.getAnnotation(RequestHeader.class);
            RequestBody body = parameter.getAnnotation(RequestBody.class);
            if (path != null) {
                Object value = mapped instanceof Map<?, ?> map ? map.get(path.value()) : null;
                args.add(value != null ? value : resolveValue(path.value(), execution));
            }
            else if (header != null) args.add(execution.workflowContext().metadata().get(header.value()));
            else if (body != null) args.add(mapped);
            else args.add(mapped);
        }
        return args.toArray();
    }

    private Object resolveValue(String key, StepExecutionContext execution) {
        String value = execution.workflowContext().metadata().get(key);
        if (value != null) return value;
        if (execution.input() instanceof Map<?, ?> map) return map.get(key);
        return execution.input();
    }

    private Exception unwrap(Throwable t) {
        return t instanceof Exception e ? e : new RuntimeException(t);
    }
}
