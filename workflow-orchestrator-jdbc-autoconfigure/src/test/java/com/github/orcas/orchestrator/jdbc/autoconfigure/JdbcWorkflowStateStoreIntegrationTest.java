package com.github.orcas.orchestrator.jdbc.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.orcas.orchestrator.core.model.Metadata;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

class JdbcWorkflowStateStoreIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
      .withDatabaseName("workflow")
      .withUsername("workflow")
      .withPassword("workflow");

  static {
    POSTGRES.start();
  }

  private static NamedParameterJdbcTemplate jdbc;
  private static JdbcWorkflowStateStore stateStore;

  @BeforeAll
  static void setUpDatabase() {
    var dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
    new WorkflowJdbcSchemaInitializer(dataSource, new WorkflowJdbcProperties()).afterPropertiesSet();
    jdbc = new NamedParameterJdbcTemplate(dataSource);
    stateStore = new JdbcWorkflowStateStore(jdbc, new ObjectMapper(), new DataSourceTransactionManager(dataSource), true);
  }

  @BeforeEach
  void cleanDatabase() {
    jdbc.getJdbcTemplate().execute("""
        TRUNCATE TABLE workflow_step_context_log,
            workflow_step_context,
            workflow_metadata_log,
            workflow_context_log,
            workflow_step_log,
            workflow_log,
            workflow_metadata,
            workflow_context,
            workflow_step,
            workflow
        RESTART IDENTITY CASCADE
        """);
  }

  @Test
  void initialStateAndStepSnapshotEachUseOneDatabaseCommand() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var countingJdbc = new NamedParameterJdbcTemplate(jdbc.getJdbcTemplate()) {
      @Override public int update(String sql, org.springframework.jdbc.core.namedparam.SqlParameterSource params) {
        calls.incrementAndGet();
        return super.update(sql, params);
      }
    };
    var store = new JdbcWorkflowStateStore(countingJdbc, new ObjectMapper(),
        new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource()), true);
    store.start("batched-start", "fulfilment-flow", new WorkflowContext("draft", new Metadata()));
    assertThat(calls.get()).isEqualTo(1);
    assertThat(count("workflow_log")).isEqualTo(1);
    assertThat(count("workflow_context_log")).isEqualTo(1);
    assertThat(count("workflow_metadata_log")).isEqualTo(1);
    store.saveStepContext(new com.github.orcas.orchestrator.core.model.StepContext(
        "batched-start", "fulfilment-flow", "charge", null, "draft", "charged", Map.of(), java.time.Instant.now()));
    assertThat(calls.get()).isEqualTo(2);
    assertThat(count("workflow_step_context_log")).isEqualTo(1);
    assertThat(store.stepContext("batched-start", "charge")).isPresent();
    store.start("batched-start", "fulfilment-flow", new WorkflowContext("updated", new Metadata()));
    assertThat(calls.get()).isEqualTo(3);
    assertThat(count("workflow")).isEqualTo(1);
    assertThat(count("workflow_log")).isEqualTo(2);
    assertThat(count("workflow_context_log")).isEqualTo(2);
  }

  @Test
  void statusAndAllAuditRowsUseOneDatabaseCommand() {
    stateStore.start("batched-status", "fulfilment-flow", new WorkflowContext("draft", new Metadata()));
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var countingJdbc = new NamedParameterJdbcTemplate(jdbc.getJdbcTemplate()) {
      @Override public int update(String sql, org.springframework.jdbc.core.namedparam.SqlParameterSource params) {
        calls.incrementAndGet();
        return super.update(sql, params);
      }
    };
    var store = new JdbcWorkflowStateStore(countingJdbc, new ObjectMapper(),
        new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource()), true);
    store.record(StatusEvent.of("batched-status", "fulfilment-flow", "charge", Status.SUCCESS,
        Map.of("requestId", "request-batched"), "charged"), "PaymentStep");
    assertThat(calls.get()).isEqualTo(1);
    assertThat(columnValue("workflow", "status", "batched-status")).isEqualTo("RUNNING");
    assertThat(columnValue("workflow_step", "state", "batched-status")).isEqualTo("SUCCESS");
    assertThat(count("workflow_step_log")).isEqualTo(1);
    assertThat(count("workflow_log")).isEqualTo(2);
    assertThat(count("workflow_metadata_log")).isEqualTo(2);
    assertThat(store.context("batched-status").metadata().get("requestId")).isEqualTo("request-batched");
  }

  @Test
  void standaloneJdbcManagerStillParticipatesInBusinessRollback() {
    var manager = new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource());
    var store = new JdbcWorkflowStateStore(jdbc, new ObjectMapper(), manager, true);
    new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status -> {
      store.start("business-rollback", "fulfilment-flow", new WorkflowContext("draft", new Metadata()));
      status.setRollbackOnly();
    });
    assertThat(count("workflow")).isZero();
    assertThat(count("workflow_context")).isZero();
    assertThat(count("workflow_log")).isZero();
  }

  @Test
  void stepContextLoadsWorkflowNameWithoutNestedConnection() {
    var id = "single-connection-context";
    var context = new WorkflowContext(Map.of("value", "draft"), new Metadata());
    stateStore.start(id, "fulfilment-flow", context);
    var step = new StepContext(id, "fulfilment-flow", "charge", null,
        context.businessInput(), "charged", Map.of(), Instant.now());
    stateStore.saveStepContext(step);
    var store = new JdbcWorkflowStateStore(jdbc, new ObjectMapper(),
        new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource())) {
      @Override public String workflowName(String workflowId) {
        throw new AssertionError("Row mapping must not open another connection");
      }
    };
    assertThat(store.stepContext(id, "charge")).isPresent();
    assertThat(store.stepContext(id, "charge").orElseThrow().workflow()).isEqualTo("fulfilment-flow");
  }

  @Test
  void stepResultRollsBackBothSnapshotsWhenContextPersistenceFails() {
    var id = "atomic-step-result";
    stateStore.start(id, "fulfilment-flow", new WorkflowContext("draft", new Metadata()));
    var step = new StepContext(id, "fulfilment-flow", "charge", null,
        "draft", "charged", Map.of(), Instant.now());
    assertThatThrownBy(() -> stateStore.saveStepResult(step, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(stateStore.stepContext(id, "charge")).isEmpty();
    assertThat(count("workflow_step_context_log")).isZero();
    assertThat(stateStore.context(id).businessInput()).isEqualTo("draft");
  }

  @Test
  @DisplayName("start persists workflow state, context, metadata, and audit rows")
  void startPersistsInitialState() {
    var id = "workflow-start";
    var workflow = "fulfilment-flow";

    stateStore.start(id, workflow, new WorkflowContext(new Payload("draft", 1), new Metadata(Map.of("tenant", "acme"))));

    assertThat(stateStore.workflowName(id)).isEqualTo(workflow);
    assertThat(stateStore.context(id).businessInput())
        .isEqualTo(Map.of("value", "draft", "count", 1));
    assertThat(stateStore.context(id).metadata().asMap())
        .containsEntry("tenant", "acme");
    assertThat(columnValue("workflow_context", "context_class_name", id)).isEqualTo(Payload.class.getName());
    assertThat(columnValue("workflow_metadata", "metadata_class_name", id)).isEqualTo(Metadata.class.getName());
    assertThat(columnValue("workflow", "status", id)).isEqualTo(Status.STARTED.name());
    assertThat(count("workflow")).isEqualTo(1L);
    assertThat(count("workflow_context")).isEqualTo(1L);
    assertThat(count("workflow_metadata")).isEqualTo(1L);
    assertThat(count("workflow_log")).isEqualTo(1L);
    assertThat(count("workflow_context_log")).isEqualTo(1L);
    assertThat(count("workflow_metadata_log")).isEqualTo(1L);
  }

  @Test
  @DisplayName("recording lifecycle events updates step state, retry count, metadata, and terminal workflow status")
  void recordRetryAndFinishPersistWorkflowState() {
    var id = "workflow-lifecycle";
    var workflow = "fulfilment-flow";

    stateStore.start(id, workflow, new WorkflowContext(new Payload("draft", 1), new Metadata(Map.of("tenant", "acme"))));

    stateStore.record(
        new StatusEvent(
            id,
            workflow,
            "charge-card",
            Status.RUNNING,
            Map.of("tenant", "acme", "attempt", "1"),
            "step started",
            Instant.parse("2026-01-01T10:00:00Z")),
        "com.github.orcas.workflow.ChargeCardStep");

    stateStore.recordRetry(id, "charge-card", 2, "transient deadlock");

    stateStore.record(
        new StatusEvent(
            id,
            workflow,
            "charge-card",
            Status.FAILED,
            Map.of("tenant", "acme", "attempt", "2", "error", "card-declined"),
            "step failed",
            Instant.parse("2026-01-01T10:01:00Z")),
        "com.github.orcas.workflow.ChargeCardStep");

    stateStore.finish(
        new StatusEvent(
            id,
            workflow,
            "charge-card",
            Status.FAILED,
            Map.of("tenant", "acme", "attempt", "2", "error", "card-declined"),
            "workflow failed",
            Instant.parse("2026-01-01T10:02:00Z")));

    assertThat(columnValue("workflow", "status", id)).isEqualTo(Status.FAILED.name());
    assertThat(columnValue("workflow_step", "state", id, "charge-card")).isEqualTo(Status.FAILED.name());
    assertThat(booleanValue("select date_ended is not null from workflow_step where pipeline_id=:id and step_name=:step", id, "charge-card"))
        .isTrue();
    assertThat(integerValue("select retry_count from workflow_step where pipeline_id=:id and step_name=:step", id, "charge-card"))
        .isEqualTo(1);
    assertThat(stateStore.context(id).metadata().asMap())
        .containsEntry("tenant", "acme")
        .containsEntry("attempt", "2")
        .containsEntry("error", "card-declined");
    assertThat(count("workflow_log")).isEqualTo(4L);
    assertThat(count("workflow_step_log")).isEqualTo(3L);
    assertThat(count("workflow_context_log")).isEqualTo(1L);
    assertThat(count("workflow_metadata_log")).isEqualTo(3L);
  }

  @Test
  @DisplayName("finish preserves a suspended workflow status")
  void finishPreservesSuspendedWorkflowStatus() {
    var id = "workflow-suspended";
    var workflow = "approval-flow";
    stateStore.start(id, workflow, WorkflowContext.of(Map.of("orderId", "o-1")));
    var event = new StatusEvent(id, workflow, "approval", Status.SUSPENDED, Map.of(), "awaiting approval", Instant.now());

    stateStore.record(event, "approval-step");
    stateStore.finish(event);

    assertThat(columnValue("workflow", "status", id)).isEqualTo(Status.SUSPENDED.name());
  }

  @Test
  @DisplayName("a successful step keeps the workflow running until the engine finishes it")
  void successfulIntermediateStepDoesNotFinishWorkflow() {
    var id = "workflow-intermediate-success";
    var workflow = "fulfilment-flow";
    stateStore.start(id, workflow, WorkflowContext.of(Map.of("orderId", "o-2")));
    var event = new StatusEvent(id, workflow, "reserve-inventory", Status.SUCCESS,
        Map.of(), "reserved", Instant.now());

    stateStore.record(event, "reserve-step");

    assertThat(columnValue("workflow", "status", id)).isEqualTo(Status.RUNNING.name());
    assertThat(columnValue("workflow_step", "state", id, "reserve-inventory"))
        .isEqualTo(Status.SUCCESS.name());

    stateStore.finish(event);
    assertThat(columnValue("workflow", "status", id)).isEqualTo(Status.SUCCESS.name());
  }

  @Test
  @DisplayName("saveStepContext stores and rehydrates step snapshots while updateContext refreshes the workflow context")
  void saveStepContextAndUpdateContextRoundTrip() {
    var id = "workflow-step-context";
    var workflow = "approval-flow";

    stateStore.start(id, workflow, new WorkflowContext(new Payload("draft", 1), new Metadata(Map.of("tenant", "acme"))));

    var stepContext = new StepContext(
        id,
        workflow,
        "prepare",
        "root",
        Map.of("kind", "input"),
        Map.of("result", "ready"),
        Map.of("owner", "ops"),
        Instant.parse("2026-01-01T11:00:00Z"));
    stateStore.saveStepContext(stepContext);

    assertThat(stateStore.stepContext(id, "prepare")).hasValue(stepContext);
    assertThat(count("workflow_step")).isEqualTo(1L);
    assertThat(count("workflow_step_context")).isEqualTo(1L);
    assertThat(count("workflow_step_context_log")).isEqualTo(1L);

    stateStore.updateContext(id, new WorkflowContext(new Payload("released", 2), new Metadata(Map.of("tenant", "acme", "source", "api"))));

    assertThat(stateStore.context(id).businessInput())
        .isEqualTo(Map.of("value", "released", "count", 2));
    assertThat(stateStore.context(id).metadata().asMap())
        .containsEntry("tenant", "acme")
        .containsEntry("source", "api");
    assertThat(columnValue("workflow_context", "context_class_name", id)).isEqualTo(Payload.class.getName());
    assertThat(columnValue("workflow_metadata", "metadata_class_name", id)).isEqualTo(Metadata.class.getName());
    assertThat(count("workflow_context_log")).isEqualTo(2L);
    assertThat(count("workflow_metadata_log")).isEqualTo(2L);
  }

  @Test
  @DisplayName("suspended workflow ids are returned in deterministic order")
  void suspendedWorkflowIdsAreOrderedDeterministically() {
    var workflow = "approval-flow";
    var timestamp = Instant.parse("2026-01-01T12:00:00Z");

    stateStore.start("workflow-b", workflow, new WorkflowContext(new Payload("draft", 1), new Metadata(Map.of("tenant", "acme"))));
    stateStore.start("workflow-a", workflow, new WorkflowContext(new Payload("draft", 1), new Metadata(Map.of("tenant", "acme"))));

    stateStore.record(
        new StatusEvent(
            "workflow-b",
            workflow,
            "wait-for-approval",
            Status.SUSPENDED,
            Map.of(),
            "paused",
            timestamp),
        "com.github.orcas.workflow.WaitForApprovalStep");
    stateStore.record(
        new StatusEvent(
            "workflow-a",
            workflow,
            "wait-for-approval",
            Status.SUSPENDED,
            Map.of(),
            "paused",
            timestamp),
        "com.github.orcas.workflow.WaitForApprovalStep");

    assertThat(stateStore.suspendedWorkflowIds("wait-for-approval")).containsExactly("workflow-a", "workflow-b");
  }

  private long count(String table) {
    return jdbc.getJdbcTemplate().queryForObject("select count(*) from " + table, Long.class);
  }

  private String columnValue(String table, String column, String id) {
    return jdbc.queryForObject(
        "select " + column + " from " + table + " where pipeline_id=:id",
        new MapSqlParameterSource().addValue("id", id, Types.VARCHAR),
        String.class);
  }

  private String columnValue(String table, String column, String id, String step) {
    return jdbc.queryForObject(
        "select " + column + " from " + table + " where pipeline_id=:id and step_name=:step",
        new MapSqlParameterSource().addValue("id", id, Types.VARCHAR).addValue("step", step, Types.VARCHAR),
        String.class);
  }

  private boolean booleanValue(String sql, String id, String step) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        sql,
        new MapSqlParameterSource().addValue("id", id, Types.VARCHAR).addValue("step", step, Types.VARCHAR),
        Boolean.class));
  }

  private Integer integerValue(String sql, String id, String step) {
    return jdbc.queryForObject(
        sql,
        new MapSqlParameterSource().addValue("id", id, Types.VARCHAR).addValue("step", step, Types.VARCHAR),
        Integer.class);
  }

  private record Payload(String value, int count) {
  }
}
