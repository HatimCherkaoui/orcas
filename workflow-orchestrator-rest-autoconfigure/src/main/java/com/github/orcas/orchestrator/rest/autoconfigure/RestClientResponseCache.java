package com.github.orcas.orchestrator.rest.autoconfigure;

import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Small bounded in-memory response cache for idempotent REST client methods. */
final class RestClientResponseCache {
    private final WorkflowRestClientProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedValue> values = new ConcurrentHashMap<>();

    RestClientResponseCache(WorkflowRestClientProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    Optional<Object> get(Method method, Object[] arguments) {
        String key = key(method, arguments);
        if (key == null) {
            return Optional.empty();
        }

        var cached = values.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.expiresAt().isAfter(Instant.now())) {
            return Optional.ofNullable(cached.value());
        }

        values.remove(key, cached);
        return Optional.empty();
    }

    void put(Method method, Object[] arguments, Object response) {
        String key = key(method, arguments);
        if (key == null) {
            return;
        }

        int maxEntries = properties.getCache().getMaxEntries();
        if (maxEntries <= 0) {
            return;
        }
        if (values.size() >= maxEntries) {
            values.keySet().stream().findFirst().ifPresent(values::remove);
        }
        values.put(key, new CachedValue(
                response,
                Instant.now().plus(properties.getCache().getTtl())));
    }

    private String key(Method method, Object[] arguments) {
        if (!properties.getCache().isEnabled()
                || org.springframework.http.ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
            return null;
        }
        try {
            return method.toGenericString() + "|" + objectMapper.writeValueAsString(arguments);
        } catch (Exception ignored) {
            return method.toGenericString() + "|" + Arrays.deepToString(arguments);
        }
    }


    private record CachedValue(Object value, Instant expiresAt) {
    }
}
