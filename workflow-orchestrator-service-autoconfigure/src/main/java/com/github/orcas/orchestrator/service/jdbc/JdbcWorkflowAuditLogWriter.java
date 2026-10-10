package com.github.orcas.orchestrator.service.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Types;
import java.time.OffsetDateTime;

/** Writes operator actions to the audit tables owned by the workflow schema. */
final class JdbcWorkflowAuditLogWriter {
    private static final String WORKFLOW_LOG =
            "insert into workflow_log(pipeline_id, action, snapshot_json, date_created) "
                    + "values (:id, :action, :snapshot, :created)";
    private static final String STEP_LOG =
            "insert into workflow_step_log(pipeline_id, step_name, action, snapshot_json, date_created) "
                    + "values (:id, :stepName, :action, :snapshot, :created)";
    private static final String CONTEXT_LOG =
            "insert into workflow_context_log(pipeline_id, action, snapshot_json, date_created) "
                    + "values (:id, :action, :snapshot, :created)";
    private static final String METADATA_LOG =
            "insert into workflow_metadata_log(pipeline_id, action, snapshot_json, date_created) "
                    + "values (:id, :action, :snapshot, :created)";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcWorkflowAuditLogWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void workflow(String workflowId, String action, String snapshot, OffsetDateTime created) {
        write(WORKFLOW_LOG, workflowId, null, action, snapshot, created);
    }

    void step(String workflowId, String stepName, String action, String snapshot, OffsetDateTime created) {
        write(STEP_LOG, workflowId, stepName, action, snapshot, created);
    }

    void context(String workflowId, String action, String snapshot, OffsetDateTime created) {
        write(CONTEXT_LOG, workflowId, null, action, snapshot, created);
    }

    void metadata(String workflowId, String action, String snapshot, OffsetDateTime created) {
        write(METADATA_LOG, workflowId, null, action, snapshot, created);
    }

    private void write(
            String sql,
            String workflowId,
            String stepName,
            String action,
            String snapshot,
            OffsetDateTime created) {
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", workflowId, Types.VARCHAR)
                .addValue("stepName", stepName, Types.VARCHAR)
                .addValue("action", action, Types.VARCHAR)
                .addValue("snapshot", snapshot, Types.LONGVARCHAR)
                .addValue("created", created, Types.TIMESTAMP_WITH_TIMEZONE));
    }
}
