package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

/** Maps workflow and step rows without coupling the service API to JDBC details. */
final class JdbcWorkflowViewMapper {
    private JdbcWorkflowViewMapper() {
    }

    static RowMapper<WorkflowQueryService.WorkflowSummary> summary() {
        return (rs, row) -> new WorkflowQueryService.WorkflowSummary(
                rs.getString("pipeline_id"),
                rs.getString("workflow"),
                rs.getString("status"),
                instant(rs, "date_created"),
                instant(rs, "date_updated"),
                rs.getString("current_step"));
    }

    static RowMapper<WorkflowQueryService.WorkflowDetails> details() {
        return (rs, row) -> new WorkflowQueryService.WorkflowDetails(
                rs.getString("pipeline_id"),
                rs.getString("workflow"),
                rs.getString("status"),
                instant(rs, "date_created"),
                instant(rs, "date_updated"));
    }

    static RowMapper<WorkflowQueryService.WorkflowStepView> step() {
        return (rs, row) -> new WorkflowQueryService.WorkflowStepView(
                rs.getString("pipeline_id"),
                rs.getString("workflow"),
                rs.getString("step_name"),
                rs.getString("step_type_class_name"),
                rs.getString("state"),
                rs.getInt("retry_count"),
                instant(rs, "date_started"),
                instant(rs, "date_ended"),
                instant(rs, "date_updated"));
    }

    static RowMapper<WorkflowQueryService.EntityLogView> log() {
        return (rs, row) -> new WorkflowQueryService.EntityLogView(
                rs.getLong("id"),
                rs.getString("pipeline_id"),
                rs.getString("step_name"),
                rs.getString("action"),
                rs.getString("snapshot_json"),
                instant(rs, "date_created"));
    }

    static RowMapper<WorkflowQueryService.ReplayView> replay() {
        return (rs, row) -> new WorkflowQueryService.ReplayView(
                rs.getLong("id"),
                rs.getString("pipeline_id"),
                rs.getString("step_name"),
                rs.getString("snapshot_json"),
                instant(rs, "date_created"));
    }

    static Instant instant(ResultSet rs, String column) {
        try {
            Timestamp value = rs.getTimestamp(column);
            return value == null ? null : value.toInstant();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read timestamp " + column, e);
        }
    }
}
