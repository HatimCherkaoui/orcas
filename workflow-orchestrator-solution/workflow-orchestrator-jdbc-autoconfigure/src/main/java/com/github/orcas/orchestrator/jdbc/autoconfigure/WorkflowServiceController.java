package com.github.orcas.orchestrator.jdbc.autoconfigure;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
public class WorkflowServiceController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public WorkflowServiceController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}
