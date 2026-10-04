package com.github.orcas.orchestrator.service.jdbc;

import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.core.model.StepContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.orcas.orchestrator.service.jdbc.JdbcWorkflowViewMapper.instant;

/**
 * {@link WorkflowQueryService} implementation backed by direct JDBC queries against the
 * relational workflow, workflow_step, workflow_context, workflow_metadata and
 * workflow_log tables written by {@code JdbcWorkflowStateStore} and
 * {@code JdbcWorkflowAdminService}. Read paths are intentionally simple hand-written SQL
 * (rather than an ORM) since the dashboard's query patterns are ad-hoc filters and joins
 * that map awkwardly onto entity graphs; JSON payload columns are deserialized on the
 * fly via the shared Jackson {@link ObjectMapper}.
 */
public final class JdbcWorkflowQueryService implements WorkflowQueryService {
    private static final String WORKFLOW_LOG_SQL = """
            select id,pipeline_id,null as step_name,action,snapshot_json,date_created
              from workflow_log where pipeline_id=:id order by date_created
            """;
    private static final String CONTEXT_LOG_SQL = """
            select id,pipeline_id,null as step_name,action,snapshot_json,date_created
              from workflow_context_log where pipeline_id=:id order by date_created
            """;
    private static final String METADATA_LOG_SQL = """
            select id,pipeline_id,null as step_name,action,snapshot_json,date_created
              from workflow_metadata_log where pipeline_id=:id order by date_created
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /**
     * Creates the service.
     *
     * @param jdbc   the JDBC template used to run all read queries
     * @param mapper shared Jackson mapper used to deserialize JSON payload columns
     */
    public JdbcWorkflowQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public PageResult<WorkflowSummary> search(WorkflowQuery query) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        WorkflowQuerySql.appendWorkflowFilters(where, parameters, query, "w");
        WorkflowQuerySql.appendStepExistenceFilters(where, parameters, query, "w");

        long total = jdbc.queryForObject(
                "select count(*) from workflow w " + where,
                parameters,
                Long.class);

        parameters.addValue("limit", query.size());
        parameters.addValue("offset", (long) query.page() * query.size());

        List<WorkflowSummary> rows = jdbc.query("""
                        select w.pipeline_id, w.workflow, w.status, w.date_created, w.date_updated,
                               coalesce((select ws.step_name from workflow_step ws
                                         where ws.pipeline_id = w.pipeline_id
                                         order by ws.date_updated desc limit 1), 'INIT') as current_step
                          from workflow w
                        """ + where + " order by w.date_created desc limit :limit offset :offset",
                parameters,
                JdbcWorkflowViewMapper.summary());

        int pages = (int) Math.ceil(total / (double) query.size());
        return new PageResult<>(rows, total, query.page(), query.size(), pages);
    }

    @Override
    public Optional<WorkflowDetails> find(String id) {
        return jdbc.query("""
                        select pipeline_id, workflow, status, date_created, date_updated
                          from workflow where pipeline_id=:id
                        """,
                new MapSqlParameterSource().addValue("id", id),
                rs -> rs.next()
                        ? Optional.of(JdbcWorkflowViewMapper.details().mapRow(rs, 0))
                        : Optional.empty());
    }

    @Override
    public List<WorkflowStepView> steps(String id) {
        return jdbc.query("""
                        select pipeline_id, workflow, step_name, step_type_class_name, state, retry_count,
                               date_started, date_ended, date_updated
                          from workflow_step where pipeline_id=:id order by date_started nulls last, step_name
                        """, new MapSqlParameterSource().addValue("id", id),
                JdbcWorkflowViewMapper.step());
    }

    @Override
    public WorkflowStepView step(String workflowId, String stepName) {
        return jdbc.queryForObject("""
                        select pipeline_id, workflow, step_name, step_type_class_name, state, retry_count,
                               date_started, date_ended, date_updated
                          from workflow_step where pipeline_id=:id and step_name=:stepName
                        """, new MapSqlParameterSource().addValue("id",
                        workflowId).addValue("stepName", stepName),
                JdbcWorkflowViewMapper.step());

    }

    @Override
    public Optional<StepContext> stepContext(String workflowId, String stepName) {
        return jdbc.query("""
                select s.step_name, s.parent_step_name, s.input_json, s.output_json, s.attributes_json, s.date_updated, w.workflow
                  from workflow_step_context s
                  join workflow w on w.pipeline_id=s.pipeline_id
                 where s.pipeline_id=:id and s.step_name=:step
                """, new MapSqlParameterSource().addValue("id", workflowId).addValue("step", stepName), rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new StepContext(
                    workflowId,
                    rs.getString("workflow"),
                    rs.getString("step_name"),
                    rs.getString("parent_step_name"),
                    readObject(rs.getString("input_json")),
                    readObject(rs.getString("output_json")),
                    readObjectMap(rs.getString("attributes_json")),
                    instant(rs, "date_updated")));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(String json) {
        try { return mapper.readValue(json, Map.class); }
        catch (Exception e) { throw new IllegalStateException("Invalid step attributes JSON", e); }
    }

    @Override
    public Optional<ContextView> context(String id) {
        return jdbc.query("""
                        select pipeline_id, context_json, context_class_name, date_created, date_updated
                          from workflow_context where pipeline_id=:id
                        """, new MapSqlParameterSource().addValue("id", id),
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new ContextView(rs.getString("pipeline_id"), rs.getString("context_class_name"),
                            readObject(rs.getString("context_json")), instant(rs, "date_created"), instant(rs, "date_updated")));
                });
    }

    @Override
    public Optional<MetadataView> metadata(String id) {
        return jdbc.query("""
                        select pipeline_id, metadata_json, metadata_class_name, date_created, date_updated
                          from workflow_metadata where pipeline_id=:id
                        """, new MapSqlParameterSource().addValue("id", id),
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new MetadataView(rs.getString("pipeline_id"), rs.getString("metadata_class_name"),
                            readMap(rs.getString("metadata_json")), instant(rs, "date_created"), instant(rs, "date_updated")));
                });
    }

    @Override
    public List<EntityLogView> workflowLogs(String id) {
        return logs("workflow_log", id, null);
    }

    @Override
    public List<EntityLogView> stepLogs(String id, String stepName) {
        return logs("workflow_step_log", id, stepName);
    }

    @Override
    public List<EntityLogView> contextLogs(String id) {
        return logs("workflow_context_log", id, null);
    }

    @Override
    public List<EntityLogView> metadataLogs(String id) {
        return logs("workflow_metadata_log", id, null);
    }

    @Override
    public List<ReplayView> replays(String id) {
        return jdbc.query("""
                        select id, pipeline_id, step_name, snapshot_json, date_created
                          from workflow_step_log
                         where pipeline_id=:id and action='REPLAY_REQUESTED'
                         order by date_created desc, id desc
                        """,
                new MapSqlParameterSource().addValue("id", id),
                JdbcWorkflowViewMapper.replay());
    }

    private List<EntityLogView> logs(String table, String id, String stepName) {
        String sql = switch (table) {
            case "workflow_log" -> WORKFLOW_LOG_SQL;
            case "workflow_step_log" ->
                    "select id,pipeline_id,step_name,action,snapshot_json,date_created from workflow_step_log where pipeline_id=:id " +
                            (stepName == null ? "" : "and step_name=:stepName ") + "order by date_created";
            case "workflow_context_log" -> CONTEXT_LOG_SQL;
            case "workflow_metadata_log" -> METADATA_LOG_SQL;
            default -> throw new IllegalArgumentException("Unsupported log table");
        };
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", id);
        if (stepName != null) p.addValue("stepName", stepName);
        return jdbc.query(sql, p, JdbcWorkflowViewMapper.log());
    }

    private Object readObject(String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid context JSON", e);
        }
    }

    private Map<String, String> readMap(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Invalid metadata JSON", e);
        }
    }

}

