package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;

/**
 * JDBC-backed {@link WorkflowAdminService}: applies operator-driven overrides directly
 * against the persisted state tables and appends a corresponding {@code ADMIN_UPDATE}
 * entry to the relevant audit log table, and delegates replays to the
 * {@link WorkflowEngine}.
 */
public final class JdbcWorkflowAdminService implements WorkflowAdminService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WorkflowEngine engine;

    public JdbcWorkflowAdminService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, WorkflowEngine engine) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.engine = engine;
    }

    @Override
    public void updateWorkflowStatus(String id, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("update workflow set status=:status,date_updated=:updated where pipeline_id=:id",
                new MapSqlParameterSource().addValue("status", status, Types.VARCHAR)
                        .addValue("updated", now, Types.TIMESTAMP)
                        .addValue("id", id, Types.VARCHAR)), "workflow", id);
        log("workflow_log", id, null, "ADMIN_UPDATE", write(Map.of("status", status)), now);
    }

    @Override
    public void updateStepState(String id, String stepName, String state) {
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("""
                update workflow_step set state=:state,
                       date_ended=case when :state in ('SUCCESS','FAILED','SKIPPED') then :now else date_ended end,
                       date_updated=:now where pipeline_id=:id and step_name=:step
                """, new MapSqlParameterSource()
                .addValue("state", state, Types.VARCHAR).addValue("now", now, Types.TIMESTAMP)
                .addValue("id", id, Types.VARCHAR).addValue("step", stepName, Types.VARCHAR)), "step", stepName);
        log("workflow_step_log", id, stepName, "ADMIN_UPDATE", write(Map.of("state", state)), now);
    }

    @Override
    public void replaceContext(String id, Object context) {
        String json = write(context);
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("""
                update workflow_context set context_json=:json,context_class_name=:className,date_updated=:now where pipeline_id=:id
                """, new MapSqlParameterSource().addValue("json", json, Types.LONGVARCHAR)
                .addValue("className", context == null ? null : context.getClass().getName(), Types.VARCHAR)
                .addValue("now", now, Types.TIMESTAMP).addValue("id", id, Types.VARCHAR)), "context", id);
        log("workflow_context_log", id, null, "ADMIN_UPDATE", json, now);
    }

    @Override
    public void replaceMetadata(String id, Map<String, String> metadata) {
        String json = write(metadata);
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("""
                update workflow_metadata set metadata_json=:json,date_updated=:now where pipeline_id=:id
                """, new MapSqlParameterSource().addValue("json", json, Types.LONGVARCHAR)
                .addValue("now", now, Types.TIMESTAMP).addValue("id", id, Types.VARCHAR)), "metadata", id);
        log("workflow_metadata_log", id, null, "ADMIN_UPDATE", json, now);
    }

    @Override
    public void replayStep(String pipelineId, String stepName) { engine.replay(pipelineId, stepName); }

    private void log(String table, String id, String step, String action, String snapshot, Timestamp at) {
        String sql = table.equals("workflow_step_log")
                ? "insert into workflow_step_log (pipeline_id,step_name,action,snapshot_json,date_created) values (:id,:step,:action,:snapshot,:at)"
                : "insert into " + table + " (pipeline_id,action,snapshot_json,date_created) values (:id,:action,:snapshot,:at)";
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id, Types.VARCHAR).addValue("action", action, Types.VARCHAR)
                .addValue("snapshot", snapshot, Types.LONGVARCHAR).addValue("at", at, Types.TIMESTAMP);
        if (step != null) p.addValue("step", step, Types.VARCHAR);
        jdbc.update(sql, p);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON value", e);
        }
    }

    private void requireUpdated(int count, String type, String id) {
        if (count == 0) throw new IllegalArgumentException("No " + type + " found: " + id);
    }
}
