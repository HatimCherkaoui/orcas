package com.github.orcas.orchestrator.jdbc.autoconfigure;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/** Converts persisted workflow values to and from JSON. */
final class JdbcJsonCodec {
    private final ObjectMapper mapper;

    JdbcJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to serialize workflow state", error);
        }
    }

    Object read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to deserialize workflow state", error);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> readMap(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to deserialize workflow attributes", error);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, String> readStringMap(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to deserialize workflow metadata", error);
        }
    }
}
