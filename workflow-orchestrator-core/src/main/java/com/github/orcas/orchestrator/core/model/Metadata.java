package com.github.orcas.orchestrator.core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Metadata {
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public Metadata() {
    }

    public Metadata(Map<String, String> initial) {
        if (initial != null) values.putAll(initial);
    }

    public String get(String k) {
        return values.get(k);
    }

    public void put(String k, String v) {
        if (k != null && v != null) values.put(k, v);
    }

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
