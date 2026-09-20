package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JDBC-backed {@link WorkflowAdminService}: applies operator-driven overrides directly
 * against the persisted state tables and appends a corresponding {@code ADMIN_UPDATE}
 * entry to the relevant audit log table, and delegates replays to the
 * {@link WorkflowEngine}.
 */
public final class JdbcWorkflowAdminService implements WorkflowAdminService {
    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowAdminService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WorkflowEngine engine;

    private record ReplayTarget(String workflowId, String stepName) {
    }

    public JdbcWorkflowAdminService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, WorkflowEngine engine) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.engine = engine;
    }

    @Override
    public void updateWorkflowStatus(String workflowId, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update(
                "update workflow set status=:status,date_updated=:updated where pipeline_id=:id",
                new MapSqlParameterSource()
                        .addValue("status", status, Types.VARCHAR)
                        .addValue("updated", now, Types.TIMESTAMP)
                        .addValue("id", workflowId, Types.VARCHAR)),
                "workflow", workflowId);
        log("workflow_log", workflowId, null, "ADMIN_UPDATE", write(Map.of("status", status)), now);
    }

    @Override
    public void updateStepState(String workflowId, String stepName, String state) {
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("""
                update workflow_step set state=:state,
                       date_ended=case when :state in ('SUCCESS','FAILED','SKIPPED') then :now else date_ended end,
                       date_updated=:now where pipeline_id=:id and step_name=:step
                """, new MapSqlParameterSource()
                .addValue("state", state, Types.VARCHAR)
                .addValue("now", now, Types.TIMESTAMP)
                .addValue("id", workflowId, Types.VARCHAR)
                .addValue("step", stepName, Types.VARCHAR)),
                "step", stepName);
        log("workflow_step_log", workflowId, stepName, "ADMIN_UPDATE", write(Map.of("state", state)), now);
    }

    @Override
    public void replaceContext(String workflowId, Object context) {
        String json = write(context);
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("""
                update workflow_context set context_json=:json,context_class_name=:className,date_updated=:now where pipeline_id=:id
                """, new MapSqlParameterSource()
                .addValue("json", json, Types.LONGVARCHAR)
                .addValue("className", context == null ? null : context.getClass().getName(), Types.VARCHAR)
                .addValue("now", now, Types.TIMESTAMP)
                .addValue("id", workflowId, Types.VARCHAR)),
                "context", workflowId);
        log("workflow_context_log", workflowId, null, "ADMIN_UPDATE", json, now);
    }

    @Override
    public void replaceMetadata(String workflowId, Map<String, String> metadata) {
        String json = write(metadata);
        Timestamp now = Timestamp.from(Instant.now());
        requireUpdated(jdbc.update("""
                update workflow_metadata set metadata_json=:json,date_updated=:now where pipeline_id=:id
                """, new MapSqlParameterSource()
                .addValue("json", json, Types.LONGVARCHAR)
                .addValue("now", now, Types.TIMESTAMP)
                .addValue("id", workflowId, Types.VARCHAR)),
                "metadata", workflowId);
        log("workflow_metadata_log", workflowId, null, "ADMIN_UPDATE", json, now);
    }

    @Override
    public void replayStep(String workflowId, String stepName) {
        engine.replay(workflowId, stepName);
    }

    @Override
    public BatchReplayResult replaySuspendedSteps(WorkflowQuery query) {
        List<ReplayTarget> targets = suspendedReplayTargets(query);
        int replayed = 0;
        int failed = 0;
        for (ReplayTarget target : targets) {
            try {
                engine.replay(target.workflowId(), target.stepName());
                replayed++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Batch replay failed for workflow instance {} step '{}': {}",
                        target.workflowId(), target.stepName(), e.getMessage());
            }
        }
        return new BatchReplayResult(targets.size(), replayed, failed);
    }

    private List<ReplayTarget> suspendedReplayTargets(WorkflowQuery query) {
        StringBuilder sql = new StringBuilder("""
                select ws.pipeline_id, ws.step_name
                  from workflow_step ws
                  join workflow w on w.pipeline_id = ws.pipeline_id
                 where ws.state = 'SUSPENDED'
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendWorkflowFilters(sql, parameters, query);
        sql.append(" order by ws.date_updated asc, ws.pipeline_id asc, ws.step_name asc");
        return jdbc.query(sql.toString(), parameters,
                (rs, rowNum) -> new ReplayTarget(rs.getString("pipeline_id"), rs.getString("step_name")));
    }

    private void appendWorkflowFilters(StringBuilder sql, MapSqlParameterSource parameters, WorkflowQuery query) {
        if (query.workflowId() != null && !query.workflowId().isBlank()) {
            sql.append(" and w.pipeline_id = :workflowId");
            parameters.addValue("workflowId", query.workflowId(), Types.VARCHAR);
        }
        if (query.workflow() != null && !query.workflow().isBlank()) {
            sql.append(" and w.workflow = :workflow");
            parameters.addValue("workflow", query.workflow(), Types.VARCHAR);
        }
        if (query.status() != null && !query.status().isBlank()) {
            sql.append(" and w.status = :status");
            parameters.addValue("status", query.status(), Types.VARCHAR);
        }
        if (query.stepName() != null && !query.stepName().isBlank()) {
            sql.append(" and ws.step_name = :stepName");
            parameters.addValue("stepName", query.stepName(), Types.VARCHAR);
        }
        if (query.stepStatus() != null && !query.stepStatus().isBlank()) {
            sql.append(" and ws.state = :stepStatus");
            parameters.addValue("stepStatus", query.stepStatus(), Types.VARCHAR);
        }
        if (query.metadataKey() != null && !query.metadataKey().isBlank() && query.metadataValue() != null) {
            sql.append(" and exists (select 1 from workflow_metadata wm where wm.pipeline_id = w.pipeline_id and cast(wm.metadata_json as jsonb) ->> :metadataKey = :metadataValue)");
            parameters.addValue("metadataKey", query.metadataKey(), Types.VARCHAR);
            parameters.addValue("metadataValue", query.metadataValue(), Types.VARCHAR);
        }
        if (query.createdFrom() != null) {
            sql.append(" and w.date_created >= :createdFrom");
            parameters.addValue("createdFrom", query.createdFrom());
        }
        if (query.createdTo() != null) {
            sql.append(" and w.date_created <= :createdTo");
            parameters.addValue("createdTo", query.createdTo());
        }
    }

    private void requireUpdated(int updated, String entity, String id) {
        if (updated > 0) {
            return;
        }
        throw new EmptyResultDataAccessException("No " + entity + " found for id " + id, 1);
    }

    private void log(String table, String workflowId, String stepName, String action, String snapshotJson, Timestamp now) {
        String sql = switch (table) {
            case "workflow_log" ->
                    "insert into workflow_log(pipeline_id, action, snapshot_json, date_created) values (:id, :action, :snapshot, :created)";
            case "workflow_step_log" ->
                    "insert into workflow_step_log(pipeline_id, step_name, action, snapshot_json, date_created) values (:id, :stepName, :action, :snapshot, :created)";
            case "workflow_context_log" ->
                    "insert into workflow_context_log(pipeline_id, action, snapshot_json, date_created) values (:id, :action, :snapshot, :created)";
            case "workflow_metadata_log" ->
                    "insert into workflow_metadata_log(pipeline_id, action, snapshot_json, date_created) values (:id, :action, :snapshot, :created)";
            default -> throw new IllegalArgumentException("Unsupported log table: " + table);
        };
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", workflowId, Types.VARCHAR)
                .addValue("stepName", stepName, Types.VARCHAR)
                .addValue("action", action, Types.VARCHAR)
                .addValue("snapshot", snapshotJson, Types.LONGVARCHAR)
                .addValue("created", now, Types.TIMESTAMP);
        jdbc.update(sql, parameters);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize admin payload", e);
        }
    }
}
