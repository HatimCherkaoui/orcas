package com.github.orcas.orchestrator.jdbc.autoconfigure;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.engine.WorkflowRetryStateStore;
import com.github.orcas.orchestrator.core.engine.WorkflowStateStore;
import com.github.orcas.orchestrator.core.model.Metadata;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

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
 * (see `JdbcTransactionRunner`) so those transient failures are retried
 * rather than surfacing as a permanent step failure.</p>
 */
public class JdbcWorkflowStateStore implements WorkflowStateStore, WorkflowRetryStateStore {
    private static final Logger log = Logger.getLogger(JdbcWorkflowStateStore.class.getName());

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcJsonCodec json;
    private final JdbcAuditLog auditLog;
    private final JdbcTransactionRunner transactions;

    public JdbcWorkflowStateStore(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = new JdbcJsonCodec(mapper);
        this.auditLog = new JdbcAuditLog(jdbc, json);
        this.transactions = new JdbcTransactionRunner(transactionManager, log);
    }

    @Override
    public void start(String id, String workflow, WorkflowContext context) {
        log.fine("Persisting initial JDBC state for workflow instance " + id + " ('" + workflow + "')");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String contextJson = json.write(context.businessInput());
        String metadataJson = json.write(context.metadata().asMap());
        String contextClass = context.businessInput() == null ? null : context.businessInput().getClass().getName();

        transactions.execute(() -> {
            jdbc.update("""
                            insert into workflow (pipeline_id, workflow, status, date_created, date_updated)
                            values (:id, :workflow, :status, cast(:created as timestamptz), cast(:updated as timestamptz))
                            on conflict (pipeline_id) do nothing
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id, Types.VARCHAR)
                            .addValue("workflow", workflow, Types.VARCHAR)
                            .addValue("status", Status.STARTED.name(), Types.VARCHAR)
                            .addValue("created", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));

            jdbc.update("""
                            insert into workflow_context
                                (pipeline_id, context_json, context_class_name, date_created, date_updated)
                            values (:id, :json, :className, cast(:created as timestamptz), cast(:updated as timestamptz))
                            on conflict (pipeline_id) do update set
                                context_json=excluded.context_json,
                                context_class_name=excluded.context_class_name,
                                date_updated=excluded.date_updated
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id, Types.VARCHAR)
                            .addValue("json", contextJson, Types.LONGVARCHAR)
                            .addValue("className", contextClass, Types.VARCHAR)
                            .addValue("created", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));

            jdbc.update("""
                            insert into workflow_metadata
                                (pipeline_id, metadata_json, metadata_class_name, date_created, date_updated)
                            values (:id, :json, :className, cast(:created as timestamptz), cast(:updated as timestamptz))
                            on conflict (pipeline_id) do update set
                                metadata_json=excluded.metadata_json,
                                metadata_class_name=excluded.metadata_class_name,
                                date_updated=excluded.date_updated
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", id, Types.VARCHAR)
                            .addValue("json", metadataJson, Types.LONGVARCHAR)
                            .addValue("className", Metadata.class.getName(), Types.VARCHAR)
                            .addValue("created", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));

            auditLog.workflow(id, "CREATE", Map.of("workflow", workflow, "status", Status.STARTED.name()), now);
            auditLog.context(id, "CREATE", context.businessInput(), now);
            auditLog.metadata(id, "CREATE", context.metadata().asMap(), now);
        });
    }

    //@Override
    public void record(StatusEvent event) {
        record(event, null);
    }

    @Override
    public void record(StatusEvent event, String stepTypeClassName) {
        OffsetDateTime now = event.timestamp() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : OffsetDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC);

        transactions.execute(() -> {
            if (!StepNames.INIT.equals(event.step())) {
                jdbc.update("""
                                insert into workflow_step
                                    (pipeline_id, workflow, step_name, step_type_class_name,
                                     state, failure_category, failure_code, failure_type, failure_message,
                                     failure_disposition, date_started, date_ended, date_updated)
                                values (:id, :workflow, :step, :type, :state, :failureCategory, :failureCode,
                                        :failureType, :failureMessage, :failureDisposition,
                                        :started, :ended, :updated)
                                on conflict (pipeline_id, step_name) do update set
                                    step_type_class_name=coalesce(excluded.step_type_class_name, workflow_step.step_type_class_name),
                                    state=excluded.state,
                                    failure_category=case when excluded.state in ('FAILED','SUSPENDED') then excluded.failure_category else null end,
                                    failure_code=case when excluded.state in ('FAILED','SUSPENDED') then excluded.failure_code else null end,
                                    failure_type=case when excluded.state in ('FAILED','SUSPENDED') then excluded.failure_type else null end,
                                    failure_message=case when excluded.state in ('FAILED','SUSPENDED') then excluded.failure_message else null end,
                                    failure_disposition=case when excluded.state in ('FAILED','SUSPENDED') then excluded.failure_disposition else null end,
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
                                .addValue("failureCategory", event.failure() == null ? null : event.failure().category(), Types.VARCHAR)
                                .addValue("failureCode", event.failure() == null ? null : event.failure().code(), Types.VARCHAR)
                                .addValue("failureType", event.failure() == null ? null : event.failure().exceptionType(), Types.VARCHAR)
                                .addValue("failureMessage", event.failure() == null ? null : event.failure().message(), Types.LONGVARCHAR)
                                .addValue("failureDisposition", event.failure() == null ? null : event.failure().disposition(), Types.VARCHAR)
                                .addValue("started", now, Types.TIMESTAMP_WITH_TIMEZONE)
                                .addValue("ended", terminal(event.status()) ? now : null, Types.TIMESTAMP_WITH_TIMEZONE)
                                .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));

                Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
                snapshot.put("step", event.step());
                snapshot.put("status", event.status().name());
                snapshot.put("message", event.message() == null ? "" : event.message());
                if (event.failure() != null) snapshot.put("failure", event.failure());
                auditLog.step(event.workflowId(), event.step(), event.status().name(), snapshot, now);
            }

            // A step can succeed while downstream steps still need to run. The
            // engine calls finish() only after route matching proves this event
            // ends the workflow, so step-terminal states must not make the
            // workflow row look terminal in the dashboard yet.
            String workflowStatus = switch (event.status()) {
                case SUCCESS, FAILED, SKIPPED -> Status.RUNNING.name();
                default -> event.status().name();
            };

            jdbc.update("""
                            update workflow
                               set status=:status, date_updated=:updated
                             where pipeline_id=:id
                            """,
                    new MapSqlParameterSource()
                            .addValue("status", workflowStatus, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("id", event.workflowId(), Types.VARCHAR));

            auditLog.workflow(event.workflowId(), "STATUS",
                    Map.of("status", workflowStatus, "lastStep", event.step()), now);

            if (event.metadata() != null) {
                String metadataJson = json.write(event.metadata());
                jdbc.update("""
                                update workflow_metadata
                                   set metadata_json=:json, date_updated=:updated
                                 where pipeline_id=:id
                                """,
                        new MapSqlParameterSource()
                                .addValue("json", metadataJson, Types.LONGVARCHAR)
                                .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                                .addValue("id", event.workflowId(), Types.VARCHAR));
                auditLog.metadata(event.workflowId(), "UPDATE", event.metadata(), now);
            }
        });
    }

    @Override
    public void finish(StatusEvent event) {
        OffsetDateTime now = event.timestamp() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : OffsetDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC);
        String status = event.status().name();
        log.fine("Marking workflow instance " + event.workflowId() + " as finished with status " + status);
        transactions.execute(() -> {
            jdbc.update("update workflow set status=:status,date_updated=:updated where pipeline_id=:id",
                    new MapSqlParameterSource()
                            .addValue("status", status, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("id", event.workflowId(), Types.VARCHAR));
            auditLog.workflow(event.workflowId(), "FINISH", Map.of("status", status, "lastStep", event.step()), now);
        });
    }

    private boolean terminal(Status status) {
        return status == Status.SUCCESS || status == Status.FAILED || status == Status.SKIPPED;
    }

    @Override
    public void recordRetry(String workflowId, String stepName, int attempt, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        log.fine("Recording retry attempt " + attempt + " for workflow instance " + workflowId + " step '" + stepName + "': " + reason);
        transactions.execute(() -> {
            jdbc.update("""
                            update workflow_step
                               set retry_count = retry_count + 1, date_updated = :updated
                             where pipeline_id=:id and step_name=:step
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", workflowId, Types.VARCHAR)
                            .addValue("step", stepName, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));

            auditLog.step(
                    workflowId,
                    stepName,
                    "RETRY",
                    Map.of("attempt", attempt, "reason", reason == null ? "" : reason),
                    now);
        });
    }

    @Override
    public int retryCount(String workflowId, String stepName) {
        return jdbc.query("""
                select retry_count from workflow_step where pipeline_id=:id and step_name=:step
                """, new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR)
                .addValue("step", stepName, Types.VARCHAR), rs -> rs.next() ? rs.getInt(1) : 0);
    }


    @Override
    public String workflowName(String id) {
        return jdbc.queryForObject("select workflow from workflow where pipeline_id=:id",
                new MapSqlParameterSource().addValue("id", id, Types.VARCHAR), String.class);
    }

    @Override
    public WorkflowContext context(String id) {
        Map<String, Object> row = jdbc.queryForMap("""
                        select c.context_json, m.metadata_json
                          from workflow_context c
                          join workflow_metadata m on m.pipeline_id = c.pipeline_id
                         where c.pipeline_id=:id
                        """,
                new MapSqlParameterSource().addValue("id", id, Types.VARCHAR));
        try {
            Object input = json.read(String.valueOf(row.get("context_json")));
            Map<String, String> metadata = json.readStringMap(String.valueOf(row.get("metadata_json")));
            return new WorkflowContext(input, new Metadata(metadata));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize workflow execution " + id, e);
        }
    }

    @Override
    public Optional<StepContext> stepContext(String workflowId, String stepName) {
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
                        json.read(rs.getString("input_json")),
                        json.read(rs.getString("output_json")),
                        json.readMap(rs.getString("attributes_json")),
                        rs.getTimestamp("date_updated").toInstant()));
        return rows.stream().findFirst();
    }

    @Override
    public List<String> suspendedWorkflowIds(String stepName) {
        return jdbc.queryForList("""
                        select pipeline_id
                          from workflow_step
                         where step_name=:step and state='SUSPENDED'
                         order by date_updated asc, pipeline_id asc
                        """,
                new MapSqlParameterSource().addValue("step", stepName, Types.VARCHAR), String.class);
    }

    public void saveStepContext(StepContext context) {
        OffsetDateTime now = OffsetDateTime.ofInstant(
                context.updatedAt() == null ? Instant.now() : context.updatedAt(), ZoneOffset.UTC);

        transactions.execute(() -> {
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
                            .addValue("started", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));

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
                            .addValue("input", json.write(context.input()), Types.LONGVARCHAR)
                            .addValue("output", json.write(context.output()), Types.LONGVARCHAR)
                            .addValue("attributes", json.write(context.attributes()), Types.LONGVARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE));
            auditLog.stepContextSnapshot(
                    context.workflowId(),
                    context.stepName(),
                    context,
                    now);
        });
    }

    @Override
    public void updateContext(String id, WorkflowContext context) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String contextJson = json.write(context.businessInput());
        String metadataJson = json.write(context.metadata().asMap());
        String contextClass = context.businessInput() == null ? null : context.businessInput().getClass().getName();

        transactions.execute(() -> {
            jdbc.update("""
                            update workflow_context
                               set context_json=:json, context_class_name=:className, date_updated=:updated
                             where pipeline_id=:id
                            """,
                    new MapSqlParameterSource()
                            .addValue("json", contextJson, Types.LONGVARCHAR)
                            .addValue("className", contextClass, Types.VARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("id", id, Types.VARCHAR));

            jdbc.update("""
                            update workflow_metadata
                               set metadata_json=:json, date_updated=:updated
                             where pipeline_id=:id
                            """,
                    new MapSqlParameterSource()
                            .addValue("json", metadataJson, Types.LONGVARCHAR)
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("id", id, Types.VARCHAR));

            auditLog.context(id, "UPDATE", context.businessInput(), now);
            auditLog.metadata(id, "UPDATE", context.metadata().asMap(), now);

            jdbc.update("update workflow set date_updated=:updated where pipeline_id=:id",
                    new MapSqlParameterSource()
                            .addValue("updated", now, Types.TIMESTAMP_WITH_TIMEZONE)
                            .addValue("id", id, Types.VARCHAR));
        });
    }

}
