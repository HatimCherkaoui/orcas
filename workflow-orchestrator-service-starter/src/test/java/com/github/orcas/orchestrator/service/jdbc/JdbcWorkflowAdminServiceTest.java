package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService.BatchReplayResult;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcWorkflowAdminServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private WorkflowEngine engine;

    private JdbcWorkflowAdminService service;

    @BeforeEach
    void setUp() {
        service = new JdbcWorkflowAdminService(jdbc, mapper, engine);
    }

    @Test
    void shouldReplayEveryMatchingSuspendedStepAndCountFailures() throws Exception {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> rowMapper = invocation.getArgument(2);
                    ResultSet first = row("wf-1", "step-a");
                    ResultSet second = row("wf-2", "step-b");
                    return List.of(rowMapper.mapRow(first, 0), rowMapper.mapRow(second, 1));
                });
        doAnswer(invocation -> {
            if ("wf-2".equals(invocation.getArgument(0)) && "step-b".equals(invocation.getArgument(1))) {
                throw new IllegalStateException("boom");
            }
            return null;
        }).when(engine).replay(anyString(), anyString());

        BatchReplayResult result = service.replaySuspendedSteps(new WorkflowQuery(
                null,
                "order-pipeline",
                "SUSPENDED",
                null,
                "SUSPENDED",
                null,
                null,
                Instant.parse("2026-09-20T00:00:00Z"),
                Instant.parse("2026-09-21T00:00:00Z"),
                0,
                25));

        assertThat(result.matched()).isEqualTo(2);
        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(engine).replay("wf-1", "step-a");
        verify(engine).replay("wf-2", "step-b");
    }

    private ResultSet row(String workflowId, String stepName) throws Exception {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("pipeline_id")).thenReturn(workflowId);
        when(resultSet.getString("step_name")).thenReturn(stepName);
        return resultSet;
    }
}


