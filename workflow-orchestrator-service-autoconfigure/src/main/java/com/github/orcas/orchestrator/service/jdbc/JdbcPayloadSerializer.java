package com.github.orcas.orchestrator.service.jdbc;

import tools.jackson.databind.ObjectMapper;

/** Serializes operator payloads before they are written to audit columns. */
final class JdbcPayloadSerializer {
    private final ObjectMapper mapper;

    JdbcPayloadSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    java.util.Map<String,String> readMap(String json) {
        if (json == null || json.isBlank()) return java.util.Map.of();
        try {
            var raw = mapper.readValue(json,java.util.Map.class);
            var values = new java.util.LinkedHashMap<String,String>();
            raw.forEach((key,value) -> { if (key instanceof String name && value instanceof String text) values.put(name,text); });
            return values;
        } catch (Exception error) { throw new IllegalStateException("Unable to deserialize workflow metadata",error); }
    }
    String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize workflow payload", e);
        }
    }
}
