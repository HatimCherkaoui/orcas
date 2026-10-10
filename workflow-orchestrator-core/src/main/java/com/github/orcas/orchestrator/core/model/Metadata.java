package com.github.orcas.orchestrator.core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable string metadata carried through a workflow execution. */
public final class Metadata {
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public Metadata() {
    }

    public Metadata(Map<String, String> initial) {
        if (initial != null) {
            initial.forEach(this::put);
        }
    }

    public String get(String key) {
        return values.get(key);
    }

    public void put(String key, String value) {
        if (key != null && value != null) {
            values.put(key, value);
        }
    }

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
