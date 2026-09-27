package com.github.orcas.orchestrator.service.jdbc;

import tools.jackson.databind.ObjectMapper;

/** Serializes operator payloads before they are written to audit columns. */
final class JdbcPayloadSerializer {
    private final ObjectMapper mapper;

    JdbcPayloadSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize workflow payload", e);
        }
    }
}
