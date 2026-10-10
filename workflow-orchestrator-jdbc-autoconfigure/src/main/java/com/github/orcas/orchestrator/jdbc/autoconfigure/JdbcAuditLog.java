package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Types;
import java.time.OffsetDateTime;

/** Writes append-only workflow audit snapshots to the JDBC log tables. */
final class JdbcAuditLog {
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcJsonCodec json;

    JdbcAuditLog(NamedParameterJdbcTemplate jdbc, JdbcJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void workflow(String id, String action, Object snapshot, OffsetDateTime at) {
        insert("workflow_log", id, null, action, json.write(snapshot), at);
    }

    void step(String id, String step, String action, Object snapshot, OffsetDateTime at) {
        insert("workflow_step_log", id, step, action, json.write(snapshot), at);
    }

    void context(String id, String action, Object snapshot, OffsetDateTime at) {
        insert("workflow_context_log", id, null, action, json.write(snapshot), at);
    }

    void metadata(String id, String action, Object snapshot, OffsetDateTime at) {
        insert("workflow_metadata_log", id, null, action, json.write(snapshot), at);
    }

    void stepContextSnapshot(String id, String step, Object snapshot, OffsetDateTime at) {
        jdbc.update("""
                        insert into workflow_step_context_log
                            (pipeline_id, step_name, snapshot_json, date_created)
                        values (:id,:step,:snapshot,:at)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id, Types.VARCHAR)
                        .addValue("step", step, Types.VARCHAR)
                        .addValue("snapshot", json.write(snapshot), Types.LONGVARCHAR)
                        .addValue("at", at, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insert(String table, String id, String step, String action, String snapshot, OffsetDateTime at) {
        String sql = switch (table) {
            case "workflow_log" -> """
                    insert into workflow_log (pipeline_id, action, snapshot_json, date_created)
                    values (:id, :action, :snapshot, :at)
                    """;
            case "workflow_step_log" -> """
                    insert into workflow_step_log (pipeline_id, step_name, action, snapshot_json, date_created)
                    values (:id, :step, :action, :snapshot, :at)
                    """;
            case "workflow_context_log" -> """
                    insert into workflow_context_log (pipeline_id, action, snapshot_json, date_created)
                    values (:id, :action, :snapshot, :at)
                    """;
            case "workflow_metadata_log" -> """
                    insert into workflow_metadata_log (pipeline_id, action, snapshot_json, date_created)
                    values (:id, :action, :snapshot, :at)
                    """;
            default -> throw new IllegalArgumentException("Unknown audit log table: " + table);
        };
        jdbc.update(sql, parameters(id, step, action, snapshot, at));
    }

    private static MapSqlParameterSource parameters(
            String id,
            String step,
            String action,
            String snapshot,
            OffsetDateTime at) {
        var parameters = new MapSqlParameterSource()
                .addValue("id", id, Types.VARCHAR)
                .addValue("action", action, Types.VARCHAR)
                .addValue("snapshot", snapshot, Types.LONGVARCHAR)
                .addValue("at", at, Types.TIMESTAMP_WITH_TIMEZONE);
        if (step != null) {
            parameters.addValue("step", step, Types.VARCHAR);
        }
        return parameters;
    }
}
