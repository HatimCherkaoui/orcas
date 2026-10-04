package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowReplayPublisher;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-backed {@link WorkflowAdminService}: applies operator-driven overrides directly
 * against the persisted state tables and appends a corresponding {@code ADMIN_UPDATE}
 * entry to the relevant audit log table, and delegates replays to the configured replay
 * publisher.
 */
public final class JdbcWorkflowAdminService implements WorkflowAdminService {
    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowAdminService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcPayloadSerializer serializer;
    private final JdbcWorkflowAuditLogWriter auditLog;
    private final WorkflowReplayPublisher replayPublisher;

    private record ReplayTarget(String workflowId, String stepName) {
    }

    public JdbcWorkflowAdminService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, WorkflowReplayPublisher replayPublisher) {
        this.jdbc = jdbc;
        this.serializer = new JdbcPayloadSerializer(mapper);
        this.auditLog = new JdbcWorkflowAuditLogWriter(jdbc);
        this.replayPublisher = replayPublisher;
    }

    @Override
    public void updateWorkflowStatus(String workflowId, String status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        requireUpdated(jdbc.update(
                "update workflow set status=:status,date_updated=cast(:updated as timestamptz) where pipeline_id=:id",
                new MapSqlParameterSource()
                        .addValue("status", status, Types.VARCHAR)
                        .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                        .addValue("id", workflowId, Types.VARCHAR)),
                "workflow", workflowId);
        auditLog.workflow(workflowId, "ADMIN_UPDATE", serializer.write(Map.of("status", status)), now);
    }

    @Override
    public void updateStepState(String workflowId, String stepName, String state) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        requireUpdated(jdbc.update("""
                update workflow_step set state=:state,
                       date_ended=case when :state in ('SUCCESS','FAILED','SKIPPED') then cast(:now as timestamptz) else date_ended end,
                       date_updated=cast(:now as timestamptz) where pipeline_id=:id and step_name=:step
                """, new MapSqlParameterSource()
                .addValue("state", state, Types.VARCHAR)
                .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("id", workflowId, Types.VARCHAR)
                .addValue("step", stepName, Types.VARCHAR)),
                "step", stepName);
        auditLog.step(workflowId, stepName, "ADMIN_UPDATE", serializer.write(Map.of("state", state)), now);
    }

    @Override
    public void replaceContext(String workflowId, Object context) {
        String json = serializer.write(context);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        requireUpdated(jdbc.update("""
                update workflow_context set context_json=:json,context_class_name=:className,date_updated=cast(:now as timestamptz) where pipeline_id=:id
                """, new MapSqlParameterSource()
                .addValue("json", json, Types.LONGVARCHAR)
                .addValue("className", context == null ? null : context.getClass().getName(), Types.VARCHAR)
                .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("id", workflowId, Types.VARCHAR)),
                "context", workflowId);
        auditLog.context(workflowId, "ADMIN_UPDATE", json, now);
    }

    @Override
    public void replaceMetadata(String workflowId, Map<String, String> metadata) {
        String json = serializer.write(metadata);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        requireUpdated(jdbc.update("""
                update workflow_metadata set metadata_json=:json,date_updated=cast(:now as timestamptz) where pipeline_id=:id
                """, new MapSqlParameterSource()
                .addValue("json", json, Types.LONGVARCHAR)
                .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("id", workflowId, Types.VARCHAR)),
                "metadata", workflowId);
        auditLog.metadata(workflowId, "ADMIN_UPDATE", json, now);
    }

    @Override
    public void abandonWorkflow(String workflowId, String reason) {
        String currentStatus;
        try {
            currentStatus = jdbc.queryForObject(
                    "select status from workflow where pipeline_id=:id",
                    new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
                    String.class);
        } catch (EmptyResultDataAccessException e) {
            throw new EmptyResultDataAccessException("No workflow found for id " + workflowId, 1);
        }
        if (!"RUNNING".equals(currentStatus) && !"SUSPENDED".equals(currentStatus)) {
            throw new IllegalStateException("Workflow " + workflowId + " cannot be abandoned while in status " + currentStatus);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> snapshotValues = new LinkedHashMap<>();
        if (reason != null) {
            snapshotValues.put("reason", reason);
        }
        snapshotValues.put("status", "ABANDONED");
        String snapshot = serializer.write(snapshotValues);
        requireUpdated(jdbc.update(
                "update workflow set status=:status,date_updated=cast(:updated as timestamptz) where pipeline_id=:id",
                new MapSqlParameterSource()
                        .addValue("status", "ABANDONED", Types.VARCHAR)
                        .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                        .addValue("id", workflowId, Types.VARCHAR)),
                "workflow", workflowId);

        var activeSteps = jdbc.queryForList("""
                select step_name from workflow_step
                 where pipeline_id=:id
                   and state not in ('SUCCESS','FAILED','SKIPPED','TERMINATED')
                 order by date_updated asc, step_name asc
                """, new MapSqlParameterSource().addValue("id", workflowId), String.class);
        for (String stepName : activeSteps) {
            requireUpdated(jdbc.update("""
                    update workflow_step set state=:state,
                           date_ended=cast(:now as timestamptz),
                           date_updated=cast(:now as timestamptz)
                     where pipeline_id=:id and step_name=:step
                    """, new MapSqlParameterSource()
                    .addValue("state", "TERMINATED", Types.VARCHAR)
                    .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE)
                    .addValue("id", workflowId, Types.VARCHAR)
                    .addValue("step", stepName, Types.VARCHAR)),
                    "step", stepName);
            auditLog.step(workflowId, stepName, "ABANDONED", snapshot, now);
        }
        auditLog.workflow(workflowId, "ABANDONED", snapshot, now);
    }

    @Override
    public void replayStep(String workflowId, String stepName) {
        requireReplayPublisher().publish(workflowId, stepName);
        auditReplay(workflowId, stepName);
    }

    @Override
    public BatchReplayResult replaySuspendedSteps(WorkflowQuery query) {
        List<ReplayTarget> targets = suspendedReplayTargets(query);
        int replayed = 0;
        int failed = 0;
        for (ReplayTarget target : targets) {
            try {
                requireReplayPublisher().publish(target.workflowId(), target.stepName());
                auditReplay(target.workflowId(), target.stepName());
                replayed++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Batch replay failed for workflow instance {} step '{}': {}",
                        target.workflowId(), target.stepName(), e.getMessage());
            }
        }
        return new BatchReplayResult(targets.size(), replayed, failed);
    }

    private WorkflowReplayPublisher requireReplayPublisher() {
        if (replayPublisher == null) {
            throw new IllegalStateException("Workflow replay is unavailable because Kafka replay publishing is not configured");
        }
        return replayPublisher;
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
                (rs, ignored) -> new ReplayTarget(rs.getString("pipeline_id"), rs.getString("step_name")));
    }

    private void auditReplay(String workflowId, String stepName) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        auditLog.step(workflowId, stepName, "REPLAY_REQUESTED",
                serializer.write(Map.of("workflowId", workflowId, "stepName", stepName)), now);
    }

    private void appendWorkflowFilters(StringBuilder sql, MapSqlParameterSource parameters, WorkflowQuery query) {
        WorkflowQuerySql.appendWorkflowFilters(sql, parameters, query, "w");
        WorkflowQuerySql.appendStepFilters(sql, parameters, query, "ws");
    }

    private void requireUpdated(int updated, String entity, String id) {
        if (updated > 0) {
            return;
        }
        throw new EmptyResultDataAccessException("No " + entity + " found for id " + id, 1);
    }
}
