package com.github.orcas.orchestrator.service.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowJdbcProperties;
import com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowJdbcSchemaInitializer;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

class JdbcWorkflowAdminServiceIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
      .withDatabaseName("workflow")
      .withUsername("workflow")
      .withPassword("workflow");

  static {
    POSTGRES.start();
  }

  private static NamedParameterJdbcTemplate jdbc;

  @BeforeAll
  static void setUpDatabase() {
    var dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
    new WorkflowJdbcSchemaInitializer(dataSource, new WorkflowJdbcProperties()).afterPropertiesSet();
    jdbc = new NamedParameterJdbcTemplate(dataSource);
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
  @DisplayName("replayStep writes a replay audit row after publishing")
  void replayStepWritesReplayAuditRow() {
    insertWorkflowWithStep("wf-replay", "review", "SUSPENDED");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), (workflowId, stepName) -> {});

    service.replayStep("wf-replay", "review");

    assertThat(count("workflow_step_log")).isEqualTo(1L);
    assertThat(columnValue("workflow_step_log", "action", "wf-replay", "review")).isEqualTo("REPLAY_REQUESTED");
    assertThat(columnValue("workflow_step_log", "snapshot_json", "wf-replay", "review"))
        .contains("wf-replay")
        .contains("review");
  }

  @Test
  @DisplayName("updateWorkflowStatus updates the workflow row and appends an audit log")
  void updateWorkflowStatusUpdatesWorkflowAndWritesAuditLog() {
    insertWorkflowWithStep("wf-update", "review", "RUNNING");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    service.updateWorkflowStatus("wf-update", "FAILED");

    assertThat(workflowStatus("wf-update")).isEqualTo("FAILED");
    assertThat(count("workflow_log")).isEqualTo(1L);
    assertThat(workflowLogAction("wf-update")).isEqualTo("ADMIN_UPDATE");
    assertThat(workflowLogSnapshot("wf-update")).contains("FAILED");
  }

  @Test
  @DisplayName("updateWorkflowStatus throws when the workflow does not exist")
  void updateWorkflowStatusThrowsWhenWorkflowMissing() {
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    assertThatThrownBy(() -> service.updateWorkflowStatus("wf-missing", "FAILED"))
        .isInstanceOf(EmptyResultDataAccessException.class)
        .hasMessageContaining("No workflow found for id wf-missing");

    assertThat(count("workflow_log")).isZero();
  }

  @Test
  @DisplayName("updateStepState writes terminal state and step audit log")
  void updateStepStateWritesTerminalStateAndAuditLog() {
    insertWorkflowWithStep("wf-step-update", "review", "RUNNING");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    service.updateStepState("wf-step-update", "review", "SUCCESS");

    assertThat(stepState("wf-step-update", "review")).isEqualTo("SUCCESS");
    assertThat(stepEndedAt("wf-step-update", "review")).isNotNull();
    assertThat(count("workflow_step_log")).isEqualTo(1L);
    assertThat(columnValue("workflow_step_log", "action", "wf-step-update", "review")).isEqualTo("ADMIN_UPDATE");
    assertThat(columnValue("workflow_step_log", "snapshot_json", "wf-step-update", "review")).contains("SUCCESS");
  }

  @Test
  @DisplayName("updateStepState throws when the step does not exist")
  void updateStepStateThrowsWhenStepMissing() {
    insertWorkflowOnly("wf-step-missing", "RUNNING");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    assertThatThrownBy(() -> service.updateStepState("wf-step-missing", "review", "FAILED"))
        .isInstanceOf(EmptyResultDataAccessException.class)
        .hasMessageContaining("No step found for id review");

    assertThat(count("workflow_step_log")).isZero();
  }

  @Test
  @DisplayName("replaceContext updates the stored workflow context and appends an audit log")
  void replaceContextUpdatesWorkflowContextAndWritesAuditLog() {
    insertWorkflowWithContextAndMetadata("wf-context", "RUNNING");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);
    var replacement = new LinkedHashMap<String, Object>();
    replacement.put("orderId", "o-1");
    replacement.put("attempt", 3);
    replacement.put("flags", List.of("retry"));

    service.replaceContext("wf-context", replacement);

    assertThat(contextJson("wf-context")).isEqualTo("{\"orderId\":\"o-1\",\"attempt\":3,\"flags\":[\"retry\"]}");
    assertThat(contextClassName("wf-context")).isEqualTo(LinkedHashMap.class.getName());
    assertThat(count("workflow_context_log")).isEqualTo(1L);
    assertThat(workflowLogLike("workflow_context_log", "wf-context")).contains("orderId");
  }

  @Test
  @DisplayName("replaceMetadata updates the stored workflow metadata and appends an audit log")
  void replaceMetadataUpdatesWorkflowMetadataAndWritesAuditLog() {
    insertWorkflowWithContextAndMetadata("wf-metadata", "RUNNING");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    service.replaceMetadata("wf-metadata", Map.of("tenant", "acme", "region", "eu-west-1"));

    assertThat(metadataJson("wf-metadata")).contains("tenant").contains("acme").contains("region");
    assertThat(count("workflow_metadata_log")).isEqualTo(1L);
    assertThat(workflowLogLike("workflow_metadata_log", "wf-metadata")).contains("tenant").contains("acme");
  }

  @Test
  @DisplayName("abandonWorkflow abandons the workflow, terminates active steps, and writes audit rows")
  void abandonWorkflowTerminatesActiveStepsAndWritesAuditRows() {
    insertWorkflowWithStep("wf-abandon", "review", "RUNNING");
    insertStep("wf-abandon", "settle", "SUCCESS", Instant.parse("2026-01-01T10:05:00Z"), Instant.parse("2026-01-01T10:06:00Z"));
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    service.abandonWorkflow("wf-abandon", "operator-cancelled");

    assertThat(workflowStatus("wf-abandon")).isEqualTo("ABANDONED");
    assertThat(stepState("wf-abandon", "review")).isEqualTo("TERMINATED");
    assertThat(stepState("wf-abandon", "settle")).isEqualTo("SUCCESS");
    assertThat(stepEndedAt("wf-abandon", "review")).isNotNull();
    assertThat(count("workflow_log")).isEqualTo(1L);
    assertThat(count("workflow_step_log")).isEqualTo(1L);
    assertThat(workflowLogAction("wf-abandon")).isEqualTo("ABANDONED");
    assertThat(columnValue("workflow_step_log", "action", "wf-abandon", "review")).isEqualTo("ABANDONED");
    assertThat(columnValue("workflow_step_log", "snapshot_json", "wf-abandon", "review")).contains("operator-cancelled");
  }

  @Test
  @DisplayName("abandonWorkflow throws when the workflow does not exist")
  void abandonWorkflowThrowsWhenWorkflowMissing() {
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    assertThatThrownBy(() -> service.abandonWorkflow("wf-missing-abandon", "operator-cancelled"))
        .isInstanceOf(EmptyResultDataAccessException.class)
        .hasMessageContaining("No workflow found for id wf-missing-abandon");

    assertThat(count("workflow_log")).isZero();
    assertThat(count("workflow_step_log")).isZero();
  }

  @Test
  @DisplayName("abandonWorkflow rejects non-running or non-suspended workflows")
  void abandonWorkflowRejectsCompletedWorkflows() {
    insertWorkflowOnly("wf-abandon-invalid", "SUCCESS");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    assertThatThrownBy(() -> service.abandonWorkflow("wf-abandon-invalid", "operator-cancelled"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be abandoned");

    assertThat(workflowStatus("wf-abandon-invalid")).isEqualTo("SUCCESS");
    assertThat(count("workflow_log")).isZero();
    assertThat(count("workflow_step_log")).isZero();
  }

  @Test
  @DisplayName("abandonWorkflow accepts a missing reason and still records the abandoned workflow snapshot")
  void abandonWorkflowAllowsNullReason() {
    insertWorkflowWithStep("wf-abandon-null-reason", "review", "RUNNING");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    service.abandonWorkflow("wf-abandon-null-reason", null);

    assertThat(workflowStatus("wf-abandon-null-reason")).isEqualTo("ABANDONED");
    assertThat(stepState("wf-abandon-null-reason", "review")).isEqualTo("TERMINATED");
    assertThat(workflowLogSnapshot("wf-abandon-null-reason")).contains("\"status\":\"ABANDONED\"")
        .doesNotContain("\"reason\":null");
  }

  @Test
  @DisplayName("replayStep does not write replay history when replay publishing is unavailable")
  void replayStepWithoutPublisherDoesNotPersistAuditRow() {
    insertWorkflowWithStep("wf-missing-publisher", "review", "SUSPENDED");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), null);

    assertThatThrownBy(() -> service.replayStep("wf-missing-publisher", "review"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Workflow replay is unavailable");

    assertThat(count("workflow_step_log")).isZero();
  }

  @Test
  @DisplayName("replaySuspendedSteps records replay history only for successful publishes")
  void replaySuspendedStepsRecordsSuccessfulPublishesOnly() {
    insertWorkflowWithStep("wf-a", "review-a", "SUSPENDED");
    insertWorkflowWithStep("wf-b", "review-b", "FAILED");

    WorkflowAdminService service = new JdbcWorkflowAdminService(
        jdbc,
        new ObjectMapper(),
        (workflowId, stepName) -> {
            if ("review-b".equals(stepName)) {
                throw new RuntimeException("boom");
            }
        });

    var result = service.replaySuspendedSteps(new WorkflowQuery(null, null, null, null, null, null, null, null, null, 0, 25));

    assertThat(result.matched()).isEqualTo(2);
    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(count("workflow_step_log")).isEqualTo(1L);
    assertThat(columnValue("workflow_step_log", "step_name", "wf-a", "review-a")).isEqualTo("review-a");
  }

  @Test
  @DisplayName("replayStep rejects a step that is not failed or suspended")
  void replayStepRejectsSuccessfulStep() {
    insertWorkflowWithStep("wf-complete-step", "review", "SUCCESS");
    WorkflowAdminService service = new JdbcWorkflowAdminService(jdbc, new ObjectMapper(), (id, step) -> {});

    assertThatThrownBy(() -> service.replayStep("wf-complete-step", "review"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be replayed while in state SUCCESS");
    assertThat(count("workflow_step_log")).isZero();
  }

  private void insertWorkflowWithStep(String workflowId, String stepName, String state) {
    insertWorkflowOnly(workflowId, state);
    insertStep(workflowId, stepName, state, Instant.parse("2026-01-01T10:00:00Z"), null);
  }

  private void insertWorkflowWithContextAndMetadata(String workflowId, String status) {
    OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    insertWorkflowOnly(workflowId, status);
    jdbc.update("""
            insert into workflow_context (pipeline_id, context_json, context_class_name, date_created, date_updated)
            values (:id, :json, :className, cast(:now as timestamptz), cast(:now as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("json", "{\"initial\":true}", Types.LONGVARCHAR)
            .addValue("className", Map.class.getName(), Types.VARCHAR)
            .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE));

    jdbc.update("""
            insert into workflow_metadata (pipeline_id, metadata_json, metadata_class_name, date_created, date_updated)
            values (:id, :json, :className, cast(:now as timestamptz), cast(:now as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("json", "{\"tenant\":\"old\"}", Types.LONGVARCHAR)
            .addValue("className", Map.class.getName(), Types.VARCHAR)
            .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertWorkflowOnly(String workflowId, String status) {
    OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    jdbc.update("""
            insert into workflow (pipeline_id, workflow, status, date_created, date_updated)
            values (:id, :workflow, :status, cast(:now as timestamptz), cast(:now as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("workflow", "demo-workflow", Types.VARCHAR)
            .addValue("status", status, Types.VARCHAR)
            .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertStep(String workflowId, String stepName, String state, Instant startedAt, Instant endedAt) {
    OffsetDateTime started = OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC);
    OffsetDateTime ended = endedAt == null ? null : OffsetDateTime.ofInstant(endedAt, ZoneOffset.UTC);
    jdbc.update("""
            insert into workflow_step
                (pipeline_id, workflow, step_name, step_type_class_name, state, retry_count, date_started, date_ended, date_updated)
            values (:id, :workflow, :step, :type, :state, 0, cast(:started as timestamptz), cast(:ended as timestamptz), cast(:updated as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("workflow", "demo-workflow", Types.VARCHAR)
            .addValue("step", stepName, Types.VARCHAR)
            .addValue("type", "com.github.orcas.demo.Step", Types.VARCHAR)
            .addValue("state", state, Types.VARCHAR)
            .addValue("started", started, Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("ended", ended, Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("updated", ended == null ? started : ended, Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private long count(String table) {
    return jdbc.getJdbcTemplate().queryForObject("select count(*) from " + table, Long.class);
  }

  private String columnValue(String table, String column, String workflowId, String stepName) {
    return jdbc.queryForObject(
        "select " + column + " from " + table + " where pipeline_id=:id and step_name=:step",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR).addValue("step", stepName, Types.VARCHAR),
        String.class);
  }

  private String workflowStatus(String workflowId) {
    return jdbc.queryForObject(
        "select status from workflow where pipeline_id=:id",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

  private String workflowLogAction(String workflowId) {
    return jdbc.queryForObject(
        "select action from workflow_log where pipeline_id=:id order by id desc limit 1",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

  private String workflowLogSnapshot(String workflowId) {
    return jdbc.queryForObject(
        "select snapshot_json from workflow_log where pipeline_id=:id order by id desc limit 1",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

  private String workflowLogLike(String table, String workflowId) {
    return jdbc.queryForObject(
        "select snapshot_json from " + table + " where pipeline_id=:id order by id desc limit 1",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

  private String stepState(String workflowId, String stepName) {
    return jdbc.queryForObject(
        "select state from workflow_step where pipeline_id=:id and step_name=:step",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR).addValue("step", stepName, Types.VARCHAR),
        String.class);
  }

  private OffsetDateTime stepEndedAt(String workflowId, String stepName) {
    return jdbc.queryForObject(
        "select date_ended from workflow_step where pipeline_id=:id and step_name=:step",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR).addValue("step", stepName, Types.VARCHAR),
        OffsetDateTime.class);
  }

  private String contextJson(String workflowId) {
    return jdbc.queryForObject(
        "select context_json from workflow_context where pipeline_id=:id",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

  private String contextClassName(String workflowId) {
    return jdbc.queryForObject(
        "select context_class_name from workflow_context where pipeline_id=:id",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

  private String metadataJson(String workflowId) {
    return jdbc.queryForObject(
        "select metadata_json from workflow_metadata where pipeline_id=:id",
        new MapSqlParameterSource().addValue("id", workflowId, Types.VARCHAR),
        String.class);
  }

}
