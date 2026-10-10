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

    /** Reads an allowlisted identifier using its canonical name or HTTP header alias. */
    public String identifier(String name) {
        String key = CorrelationIdentifiers.canonical(name);
        return key == null ? null : get(key);
    }

    public Metadata withIdentifier(String name, String value) {
        String key = CorrelationIdentifiers.canonical(name);
        if (key == null || !CorrelationIdentifiers.valid(key, value)) throw new IllegalArgumentException("Invalid identifier: " + name);
        put(key, value);
        return this;
    }

    public void remove(String key) { if (key != null) values.remove(key); }

    public Map<String, String> identifiers() {
        var identifiers = new java.util.LinkedHashMap<String, String>();
        values.forEach((key, value) -> { if (CorrelationIdentifiers.isIdentifier(key)) identifiers.put(key, value); });
        return Map.copyOf(identifiers);
    }

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
