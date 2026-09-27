package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowQuerySqlTest {
    @Test
    void appendsWorkflowFiltersOnlyWhenValuesArePresent() {
        var query = new WorkflowQuery(
                "wf-1", "orders", "RUNNING", null, null,
                "customer", "42", Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"), 0, 20);
        var sql = new StringBuilder(" where 1=1");
        var parameters = new MapSqlParameterSource();

        WorkflowQuerySql.appendWorkflowFilters(sql, parameters, query, "w");

        assertThat(sql).contains("w.pipeline_id = :workflowId")
                .contains("w.workflow = :workflow")
                .contains("w.status = :status")
                .contains("metadataKey")
                .contains("w.date_created >= :createdFrom")
                .contains("w.date_created <= :createdTo");
        assertThat(parameters.getValue("workflowId")).isEqualTo("wf-1");
        assertThat(parameters.getValue("metadataValue")).isEqualTo("42");
    }

    @Test
    void appendsStepExistenceFiltersForWorkflowSearch() {
        var query = new WorkflowQuery(null, null, null, "reserve", "SUCCESS",
                null, null, null, null, 0, 20);
        var sql = new StringBuilder(" where 1=1");
        var parameters = new MapSqlParameterSource();

        WorkflowQuerySql.appendStepExistenceFilters(sql, parameters, query, "w");

        assertThat(sql).contains("sx.step_name = :stepName")
                .contains("ss.state = :stepStatus")
                .contains("sx.pipeline_id = w.pipeline_id")
                .contains("ss.pipeline_id = w.pipeline_id");
    }

    @Test
    void ignoresBlankWorkflowAndStepFilters() {
        var query = new WorkflowQuery(" ", "", null, " ", null,
                " ", "value", null, null, 0, 20);
        var sql = new StringBuilder(" where 1=1");
        var parameters = new MapSqlParameterSource();

        WorkflowQuerySql.appendWorkflowFilters(sql, parameters, query, "w");
        WorkflowQuerySql.appendStepFilters(sql, parameters, query, "ws");

        assertThat(sql).hasToString(" where 1=1");
        assertThat(parameters.getValues()).isEmpty();
    }
}
