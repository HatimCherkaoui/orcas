package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Builds the common SQL predicates shared by service read and admin queries. */
final class WorkflowQuerySql {
    private WorkflowQuerySql() {
    }

    static void appendWorkflowFilters(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            WorkflowQuery query,
            String workflowAlias) {
        if (hasText(query.workflowId())) {
            sql.append(" and ").append(workflowAlias).append(".pipeline_id = :workflowId");
            parameters.addValue("workflowId", query.workflowId(), Types.VARCHAR);
        }
        if (hasText(query.workflow())) {
            sql.append(" and ").append(workflowAlias).append(".workflow = :workflow");
            parameters.addValue("workflow", query.workflow(), Types.VARCHAR);
        }
        if (hasText(query.status())) {
            sql.append(" and ").append(workflowAlias).append(".status = :status");
            parameters.addValue("status", query.status(), Types.VARCHAR);
        }
        if (hasText(query.metadataKey()) && query.metadataValue() != null) {
            sql.append(" and exists (select 1 from workflow_metadata wm")
                    .append(" where wm.pipeline_id = ").append(workflowAlias).append(".pipeline_id")
                    .append(" and cast(wm.metadata_json as jsonb) ->> :metadataKey = :metadataValue)");
            parameters.addValue("metadataKey", query.metadataKey(), Types.VARCHAR);
            parameters.addValue("metadataValue", query.metadataValue(), Types.VARCHAR);
        }
        if (query.createdFrom() != null) {
            sql.append(" and ").append(workflowAlias).append(".date_created >= :createdFrom");
            parameters.addValue("createdFrom",
                    OffsetDateTime.ofInstant(query.createdFrom(), ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (query.createdTo() != null) {
            sql.append(" and ").append(workflowAlias).append(".date_created <= :createdTo");
            parameters.addValue("createdTo",
                    OffsetDateTime.ofInstant(query.createdTo(), ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    static void appendStepFilters(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            WorkflowQuery query,
            String stepAlias) {
        if (hasText(query.stepName())) {
            sql.append(" and ").append(stepAlias).append(".step_name = :stepName");
            parameters.addValue("stepName", query.stepName(), Types.VARCHAR);
        }
        if (hasText(query.stepStatus())) {
            sql.append(" and ").append(stepAlias).append(".state = :stepStatus");
            parameters.addValue("stepStatus", query.stepStatus(), Types.VARCHAR);
        }
    }

    static void appendStepExistenceFilters(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            WorkflowQuery query,
            String workflowAlias) {
        if (hasText(query.stepName())) {
            sql.append(" and exists (select 1 from workflow_step sx")
                    .append(" where sx.pipeline_id = ").append(workflowAlias).append(".pipeline_id")
                    .append(" and sx.step_name = :stepName)");
            parameters.addValue("stepName", query.stepName(), Types.VARCHAR);
        }
        if (hasText(query.stepStatus())) {
            sql.append(" and exists (select 1 from workflow_step ss")
                    .append(" where ss.pipeline_id = ").append(workflowAlias).append(".pipeline_id")
                    .append(" and ss.state = :stepStatus)");
            parameters.addValue("stepStatus", query.stepStatus(), Types.VARCHAR);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
