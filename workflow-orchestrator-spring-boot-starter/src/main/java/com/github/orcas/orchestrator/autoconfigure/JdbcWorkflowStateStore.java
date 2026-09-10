package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.engine.WorkflowStateStore;
import com.github.orcas.orchestrator.core.model.Metadata;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;

/**
 * Durable {@link WorkflowStateStore} backed by a relational database via
 * {@link NamedParameterJdbcTemplate}. Persists the current pipeline/step state as
 * well as an append-only audit trail ({@code workflow_log}, {@code workflow_step_log},
 * {@code workflow_context_log}, {@code workflow_metadata_log}) that powers the
 * dashboard's execution history and audit views.
 *
 * <p>Because {@link com.github.orcas.orchestrator.core.engine.WorkflowEngine} may execute several
 * steps of the same workflow instance concurrently (parallel branches, async steps), more
 * than one transaction can attempt to touch the same {@code workflow}/{@code workflow_step}
 * rows at the same time. Under contention Postgres (and most RDBMSs) can report a transient
 * deadlock or serialization failure rather than simply queuing the second writer. Each
 * mutating method here therefore runs inside a short, automatically retried transaction
 * (see {@link #inRetryableTransaction(Runnable)}) so those transient failures are retried
 * rather than surfacing as a permanent step failure.</p>
 */
public class JdbcWorkflowStateStore implements WorkflowStateStore {
    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowStateStore.class);
    private static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 4;
    private static final long RETRY_BASE_DELAY_MILLIS = 20L;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public JdbcWorkflowStateStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
                                   PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs {@code body} inside a fresh transaction, automatically retrying (with a short
     * randomized backoff) on transient concurrency failures such as deadlocks or
     * serialization errors reported by the underlying database. After
     * {@value #MAX_DEADLOCK_RETRY_ATTEMPTS} failed attempts the last exception is rethrown.
     *
     * @param body the transactional unit of work to execute
     */
    private void inRetryableTransaction(Runnable body) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                transactionTemplate.executeWithoutResult(status -> body.run());
                return;
            } catch (ConcurrencyFailureException e) {
                if (attempt >= MAX_DEADLOCK_RETRY_ATTEMPTS) {
                    log.error("Giving up after {} attempts due to a persistent transient concurrency failure: {}",
                            attempt, e.toString());
                    throw e;
                }
                long delay = RETRY_BASE_DELAY_MILLIS * attempt + (long) (Math.random() * RETRY_BASE_DELAY_MILLIS);
                log.warn("Transient concurrency failure on attempt {}/{} ({}); retrying in {}ms",
                        attempt, MAX_DEADLOCK_RETRY_ATTEMPTS, e.getMessage(), delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize workflow state", e);
        }
    }

    @Override
    public void start(String id, String workflow, PipelineContext context) {
        log.debug("Persisting initial JDBC state for workflow instance {} ('{}')", id, workflow);
        Timestamp now = Timestamp.from(Instant.now());
        String contextJson = json(context.businessInput());
        String metadataJson = json(context.metadata().asMap());
        String contextClass = context.businessInput() == null ? null : context.businessInput().getClass().getName();

        inRetryableTransaction(() -> {
            jdbc.update("""
                            insert into workflow (pipeline_id, workflow, status, date_created, date_updated)
                            values (:id, :workflow, :status, :created, :updated)
                            on conflict (pipeline_id) do nothing
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id, Types.VARCHAR)
                            .addValue("workflow", workflow, Types.VARCHAR)
                            .addValue("status", Status.STARTED.name(), Types.VARCHAR)
                            .addValue("created", now, Types.TIMESTAMP)
                            .addValue("updated", now, Types.TIMESTAMP));

            jdbc.update("""
                            insert into workflow_context
                                (pipeline_id, context_json, context_class_name, date_created, date_updated)
                            values (:id, :json, :className, :created, :updated)
                            on conflict (pipeline_id) do update set
                                context_json=excluded.context_json,
                                context_class_name=excluded.context_class_name,
                                date_updated=excluded.date_updated
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id, Types.VARCHAR)
                            .addValue("json", contextJson, Types.LONGVARCHAR)
                            .addValue("className", contextClass, Types.VARCHAR)
                            .addValue("created", now, Types.TIMESTAMP)
                            .addValue("updated", now, Types.TIMESTAMP));

            jdbc.update("""
                            insert into workflow_metadata
                                (pipeline_id, metadata_json, metadata_class_name, date_created, date_updated)
                            values (:id, :json, :className, :created, :updated)
                            on conflict (pipeline_id) do update set
                                metadata_json=excluded.metadata_json,
                                metadata_class_name=excluded.metadata_class_name,
                                date_updated=excluded.date_updated
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id, Types.VARCHAR)
                            .addValue("json", metadataJson, Types.LONGVARCHAR)
                            .addValue("className", Metadata.class.getName(), Types.VARCHAR)
                            .addValue("created", now, Types.TIMESTAMP)
                            .addValue("updated", now, Types.TIMESTAMP));

            logWorkflow(id, "CREATE", json(Map.of("workflow", workflow, "status", Status.STARTED.name())), now);
            logContext(id, "CREATE", contextJson, now);
            logMetadata(id, "CREATE", metadataJson, now);
        });
    }

    @Override
    public void record(StatusEvent event) {
        record(event, null);
    }

    @Override
    public void record(StatusEvent event, String stepTypeClassName) {
        Timestamp now = event.timestamp() == null ? Timestamp.from(Instant.now()) : Timestamp.from(event.timestamp());

        inRetryableTransaction(() -> {
            if (!StepNames.INIT.name().equals(event.step())) {
                jdbc.update("""
                                insert into workflow_step
                                    (pipeline_id, workflow, step_name, step_type_class_name,
                                     state, date_started, date_ended, date_updated)
                                values (:id, :workflow, :step, :type, :state, :started, :ended, :updated)
                                on conflict (pipeline_id, step_name) do update set
                                    step_type_class_name=coalesce(excluded.step_type_class_name, workflow_step.step_type_class_name),
                                    state=excluded.state,
                                    date_started=coalesce(workflow_step.date_started, excluded.date_started),
                                    date_ended=case when excluded.state in ('SUCCESS','FAILED','SKIPPED')
                                                    then excluded.date_ended else workflow_step.date_ended end,
                                    date_updated=excluded.date_updated
                                """,
                        new MapSqlParameterSource()
                                .addValue("id", event.workflowId(), Types.VARCHAR)
                                .addValue("workflow", event.workflow(), Types.VARCHAR)
                                .addValue("step", event.step(), Types.VARCHAR)
                                .addValue("type", stepTypeClassName, Types.VARCHAR)
                                .addValue("state", event.status().name(), Types.VARCHAR)
                                .addValue("started", now, Types.TIMESTAMP)
                                .addValue("ended", terminal(event.status()) ? now : null, Types.TIMESTAMP)
                                .addValue("updated", now, Types.TIMESTAMP));

                logStep(event.workflowId(), event.step(), event.status().name(),
                        json(Map.of("step", event.step(), "status", event.status().name(),
                                "message", event.message() == null ? "" : event.message())), now);
            }

            String workflowStatus = event.status().name();

            jdbc.update("""
                            update workflow
                               set status=:status, date_updated=:updated
                             where pipeline_id=:id
                            """,
                    new MapSqlParameterSource()
                            .addValue("status", workflowStatus, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP)
                            .addValue("id", event.workflowId(), Types.VARCHAR));

            logWorkflow(event.workflowId(), "STATUS",
                    json(Map.of("status", workflowStatus, "lastStep", event.step())), now);

            if (event.metadata() != null) {
                String metadataJson = json(event.metadata());
                jdbc.update("""
                                update workflow_metadata
                                   set metadata_json=:json, date_updated=:updated
                                 where pipeline_id=:id
                                """,
                        new MapSqlParameterSource()
                                .addValue("json", metadataJson, Types.LONGVARCHAR)
                                .addValue("updated", now, Types.TIMESTAMP)
                                .addValue("id", event.workflowId(), Types.VARCHAR));
                logMetadata(event.workflowId(), "UPDATE", metadataJson, now);
            }
        });
    }

    @Override
    public void finish(StatusEvent event) {
        Timestamp now = event.timestamp() == null ? Timestamp.from(Instant.now()) : Timestamp.from(event.timestamp());
        String status = event.status() == Status.FAILED ? Status.FAILED.name() : Status.SUCCESS.name();
        log.info("Marking workflow instance {} as finished with status {}", event.workflowId(), status);
        inRetryableTransaction(() -> {
            jdbc.update("update workflow set status=:status,date_updated=:updated where pipeline_id=:id",
                    new MapSqlParameterSource()
                            .addValue("status", status, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP)
                            .addValue("id", event.workflowId(), Types.VARCHAR));
            logWorkflow(event.workflowId(), "FINISH", json(Map.of("status", status, "lastStep", event.step())), now);
        });
    }

    private boolean terminal(Status status) {
        return status == Status.SUCCESS || status == Status.FAILED || status == Status.SKIPPED;
    }

    @Override
    public void recordRetry(String workflowId, String stepName, int attempt, String reason) {
        Timestamp now = Timestamp.from(Instant.now());
        log.info("Recording retry attempt {} for workflow instance {} step '{}': {}", attempt, workflowId, stepName, reason);
        inRetryableTransaction(() -> {
            jdbc.update("""
                            update workflow_step
                               set retry_count = retry_count + 1, date_updated = :updated
                             where pipeline_id=:id and step_name=:step
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", workflowId, Types.VARCHAR)
                            .addValue("step", stepName, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP));

            logStep(workflowId, stepName, "RETRY",
                    json(Map.of("attempt", attempt, "reason", reason == null ? "" : reason)), now);
        });
    }


    @Override
    public String workflowName(String id) {
        return jdbc.queryForObject("select workflow from workflow where pipeline_id=:id",
                new MapSqlParameterSource().addValue("id", id, Types.VARCHAR), String.class);
    }

    @Override
    public PipelineContext context(String id) {
        Map<String, Object> row = jdbc.queryForMap("""
                        select c.context_json, m.metadata_json
                          from workflow_context c
                          join workflow_metadata m on m.pipeline_id = c.pipeline_id
                         where c.pipeline_id=:id
                        """,
                new MapSqlParameterSource().addValue("id", id, Types.VARCHAR));
        try {
            Object input = mapper.readValue(String.valueOf(row.get("context_json")), Object.class);
            @SuppressWarnings("unchecked")
            Map<String, String> metadata = mapper.readValue(String.valueOf(row.get("metadata_json")), Map.class);
            return new PipelineContext(input, new Metadata(metadata));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize workflow execution " + id, e);
        }
    }

    @Override
    public StepContext stepContext(String workflowId, String stepName) {
        var rows = jdbc.query("""
                select step_name, parent_step_name, input_json, output_json, attributes_json, date_updated
                  from workflow_step_context
                 where pipeline_id=:id and step_name=:step
                """,
                new MapSqlParameterSource().addValue("id", workflowId).addValue("step", stepName),
                (rs, n) -> new StepContext(
                        workflowId,
                        workflowName(workflowId),
                        rs.getString("step_name"),
                        rs.getString("parent_step_name"),
                        readJson(rs.getString("input_json")),
                        readJson(rs.getString("output_json")),
                        readMapObject(rs.getString("attributes_json")),
                        rs.getTimestamp("date_updated").toInstant()));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public void saveStepContext(StepContext context) {
        Timestamp now = Timestamp.from(context.updatedAt() == null ? Instant.now() : context.updatedAt());

        inRetryableTransaction(() -> {
            // A step context has a composite FK to workflow_step. Context persistence can
            // happen before the status event is consumed (notably for synchronous, async
            // and parallel steps), so establish the parent row first. The later status
            // event enriches this row with the concrete step class and terminal state.
            jdbc.update("""
                    insert into workflow_step
                        (pipeline_id, workflow, step_name, step_type_class_name, state,
                         date_started, date_ended, date_updated)
                    values (:id, :workflow, :step, null, 'RUNNING', :started, null, :updated)
                    on conflict (pipeline_id, step_name) do nothing
                    """,
                    new MapSqlParameterSource()
                        .addValue("id", context.workflowId(), Types.VARCHAR)
                        .addValue("workflow", context.workflow(), Types.VARCHAR)
                        .addValue("step", context.stepName(), Types.VARCHAR)
                        .addValue("started", now, Types.TIMESTAMP)
                        .addValue("updated", now, Types.TIMESTAMP));

            jdbc.update("""
                    insert into workflow_step_context
                        (pipeline_id, step_name, parent_step_name, input_json, output_json, attributes_json, date_updated)
                    values (:id,:step,:parent,:input,:output,:attributes,:updated)
                    on conflict (pipeline_id, step_name) do update set
                        parent_step_name=excluded.parent_step_name,
                        input_json=excluded.input_json,
                        output_json=excluded.output_json,
                        attributes_json=excluded.attributes_json,
                        date_updated=excluded.date_updated
                    """,
                    new MapSqlParameterSource()
                        .addValue("id", context.workflowId(), Types.VARCHAR)
                        .addValue("step", context.stepName(), Types.VARCHAR)
                        .addValue("parent", context.parentStepName(), Types.VARCHAR)
                        .addValue("input", json(context.input()), Types.LONGVARCHAR)
                        .addValue("output", json(context.output()), Types.LONGVARCHAR)
                        .addValue("attributes", json(context.attributes()), Types.LONGVARCHAR)
                        .addValue("updated", now, Types.TIMESTAMP));
            jdbc.update("""
                    insert into workflow_step_context_log
                        (pipeline_id, step_name, snapshot_json, date_created)
                    values (:id,:step,:snapshot,:at)
                    """,
                    new MapSqlParameterSource()
                        .addValue("id", context.workflowId(), Types.VARCHAR)
                        .addValue("step", context.stepName(), Types.VARCHAR)
                        .addValue("snapshot", json(context), Types.LONGVARCHAR)
                        .addValue("at", now, Types.TIMESTAMP));
        });
    }

    private Object readJson(String json) {
        if (json == null) return null;
        try { return mapper.readValue(json, Object.class); }
        catch (Exception e) { throw new IllegalStateException("Unable to deserialize step context", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapObject(String json) {
        if (json == null) return Map.of();
        try { return mapper.readValue(json, Map.class); }
        catch (Exception e) { throw new IllegalStateException("Unable to deserialize step attributes", e); }
    }

    @Override
    public void updateContext(String id, PipelineContext context) {
        Timestamp now = Timestamp.from(Instant.now());
        String contextJson = json(context.businessInput());
        String metadataJson = json(context.metadata().asMap());
        String contextClass = context.businessInput() == null ? null : context.businessInput().getClass().getName();

        inRetryableTransaction(() -> {
            jdbc.update("""
                            update workflow_context
                               set context_json=:json, context_class_name=:className, date_updated=:updated
                             where pipeline_id=:id
                            """,
                    new MapSqlParameterSource()
                            .addValue("json", contextJson, Types.LONGVARCHAR)
                            .addValue("className", contextClass, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP)
                            .addValue("id", id, Types.VARCHAR));

            jdbc.update("""
                            update workflow_metadata
                               set metadata_json=:json, date_updated=:updated
                             where pipeline_id=:id
                            """,
                    new MapSqlParameterSource()
                            .addValue("json", metadataJson, Types.LONGVARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP)
                            .addValue("id", id, Types.VARCHAR));

            logContext(id, "UPDATE", contextJson, now);
            logMetadata(id, "UPDATE", metadataJson, now);

            jdbc.update("update workflow set date_updated=:updated where pipeline_id=:id",
                    new MapSqlParameterSource()
                            .addValue("updated", now, Types.TIMESTAMP)
                            .addValue("id", id, Types.VARCHAR));
        });
    }

    private void logWorkflow(String id, String action, String snapshot, Timestamp at) {
        log("workflow_log", id, null, action, snapshot, at);
    }

    private void logStep(String id, String step, String action, String snapshot, Timestamp at) {
        log("workflow_step_log", id, step, action, snapshot, at);
    }

    private void logContext(String id, String action, String snapshot, Timestamp at) {
        log("workflow_context_log", id, null, action, snapshot, at);
    }

    private void logMetadata(String id, String action, String snapshot, Timestamp at) {
        log("workflow_metadata_log", id, null, action, snapshot, at);
    }

    private void log(String table, String id, String step, String action, String snapshot, Timestamp at) {
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
            default -> throw new IllegalArgumentException("Unknown log table " + table);
        };
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id, Types.VARCHAR)
                .addValue("action", action, Types.VARCHAR)
                .addValue("snapshot", snapshot, Types.LONGVARCHAR)
                .addValue("at", at, Types.TIMESTAMP);
        if (sql.contains(":step")) p.addValue("step", step, Types.VARCHAR);
        jdbc.update(sql, p);
    }
}

