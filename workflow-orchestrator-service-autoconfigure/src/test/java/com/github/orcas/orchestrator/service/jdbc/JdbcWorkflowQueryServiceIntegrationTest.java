package com.github.orcas.orchestrator.service.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowJdbcProperties;
import com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowJdbcSchemaInitializer;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

class JdbcWorkflowQueryServiceIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
      .withDatabaseName("workflow")
      .withUsername("workflow")
      .withPassword("workflow");

  static {
    POSTGRES.start();
  }

  private static NamedParameterJdbcTemplate jdbc;
  private static JdbcWorkflowQueryService service;

  @BeforeAll
  static void setUpDatabase() {
    var dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
    new WorkflowJdbcSchemaInitializer(dataSource, new WorkflowJdbcProperties()).afterPropertiesSet();
    jdbc = new NamedParameterJdbcTemplate(dataSource);
    service = new JdbcWorkflowQueryService(jdbc, new ObjectMapper());
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
  @DisplayName("replays returns only replay-requested rows for the workflow")
  void replaysReturnsOnlyReplayRequestedRows() {
    insertWorkflowWithStep("wf-replays", "review");
    insertStepLog("wf-replays", "review", "REPLAY_REQUESTED", "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":1}", Instant.parse("2026-01-01T10:00:00Z"));
    insertStepLog("wf-replays", "review", "FAILED", "{\"action\":\"FAILED\",\"sequence\":2}", Instant.parse("2026-01-01T10:01:00Z"));
    insertStepLog("wf-replays", "review", "REPLAY_REQUESTED", "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":3}", Instant.parse("2026-01-01T10:02:00Z"));

    var replays = service.replays("wf-replays");

    assertThat(replays).hasSize(2);
    assertThat(replays).allSatisfy(replay -> assertThat(replay.stepName()).isEqualTo("review"));
    assertThat(replays).extracting(replay -> replay.snapshotJson())
        .allSatisfy(snapshot -> assertThat(snapshot).contains("REPLAY_REQUESTED"));
  }

  @Test
  @DisplayName("replays sorts newest first and breaks ties by id descending")
  void replaysSortsNewestFirst() {
    insertWorkflowWithStep("wf-ordering", "review");
    var at = Instant.parse("2026-01-01T10:00:00Z");
    insertStepLog("wf-ordering", "review", "REPLAY_REQUESTED", "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":11}", at);
    insertStepLog("wf-ordering", "review", "REPLAY_REQUESTED", "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":12}", at);
    insertStepLog("wf-ordering", "review", "REPLAY_REQUESTED", "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":13}", Instant.parse("2026-01-01T10:05:00Z"));

    var replays = service.replays("wf-ordering");

    assertThat(replays).extracting(replay -> replay.snapshotJson())
        .containsExactly(
            "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":13}",
            "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":12}",
            "{\"action\":\"REPLAY_REQUESTED\",\"sequence\":11}");
  }

  @Test
  @DisplayName("replays returns an empty list when no replay history exists")
  void replaysReturnsEmptyListWhenMissing() {
    insertWorkflowWithStep("wf-empty", "review");
    insertStepLog("wf-empty", "review", "RUNNING", "{\"action\":\"RUNNING\",\"sequence\":1}", Instant.parse("2026-01-01T10:00:00Z"));

    assertThat(service.replays("wf-empty")).isEmpty();
  }

  @Test
  @DisplayName("find returns workflow details when the workflow exists")
  void findReturnsWorkflowDetails() {
    insertWorkflow("wf-find", "orders", "SUCCESS", Instant.parse("2026-01-02T09:00:00Z"), Instant.parse("2026-01-02T09:05:00Z"));

    var details = service.find("wf-find");

    assertThat(details).isPresent();
    assertThat(details.get().workflow()).isEqualTo("orders");
    assertThat(details.get().status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("find returns empty when the workflow does not exist")
  void findReturnsEmptyWhenMissing() {
    assertThat(service.find("wf-missing")).isEmpty();
  }

  @Test
  @DisplayName("steps returns execution rows ordered by start time then step name")
  void stepsReturnsOrderedStepViews() {
    insertWorkflow("wf-steps", "orders", "RUNNING", Instant.parse("2026-01-02T08:00:00Z"), Instant.parse("2026-01-02T08:30:00Z"));
    insertStep("wf-steps", "orders", "settle", "SUCCESS", 0,
        Instant.parse("2026-01-02T08:10:00Z"), Instant.parse("2026-01-02T08:15:00Z"), Instant.parse("2026-01-02T08:15:00Z"));
    insertStep("wf-steps", "orders", "review", "SUSPENDED", 2,
        Instant.parse("2026-01-02T08:05:00Z"), null, Instant.parse("2026-01-02T08:20:00Z"));

    var steps = service.steps("wf-steps");

    assertThat(steps).hasSize(2);
    assertThat(steps).extracting(step -> step.stepName()).containsExactly("review", "settle");
    assertThat(steps.get(0).retryCount()).isEqualTo(2);
    assertThat(steps.get(0).dateEnded()).isNull();
  }

  @Test
  @DisplayName("step returns the named step view")
  void stepReturnsNamedStep() {
    insertWorkflowWithStep("wf-step", "review");

    var step = service.step("wf-step", "review");

    assertThat(step.workflowId()).isEqualTo("wf-step");
    assertThat(step.stepName()).isEqualTo("review");
    assertThat(step.state()).isEqualTo("SUSPENDED");
  }

  @Test
  @DisplayName("stepContext returns the deserialized step context when present")
  void stepContextReturnsDeserializedStepContext() {
    insertWorkflowWithStep("wf-step-context", "review");
    insertStepContext("wf-step-context", "review", "init",
        "{\"amount\":42}", "{\"approved\":true}", "{\"attempt\":2}", Instant.parse("2026-01-03T10:05:00Z"));

    var stepContext = service.stepContext("wf-step-context", "review");

    assertThat(stepContext).isPresent();
    assertThat(stepContext.get().workflow()).isEqualTo("demo-workflow");
    assertThat(stepContext.get().parentStepName()).isEqualTo("init");
    assertThat(stepContext.get().input()).isEqualTo(Map.of("amount", 42));
    assertThat(stepContext.get().output()).isEqualTo(Map.of("approved", true));
    assertThat(stepContext.get().attributes()).isEqualTo(Map.of("attempt", 2));
  }

  @Test
  @DisplayName("stepContext returns empty when no step context exists")
  void stepContextReturnsEmptyWhenMissing() {
    insertWorkflowWithStep("wf-step-context-empty", "review");

    assertThat(service.stepContext("wf-step-context-empty", "review")).isEmpty();
  }

  @Test
  @DisplayName("context returns the deserialized workflow context when present")
  void contextReturnsDeserializedWorkflowContext() {
    insertWorkflow("wf-context", "orders", "RUNNING", Instant.parse("2026-01-03T09:00:00Z"), Instant.parse("2026-01-03T09:10:00Z"));
    insertWorkflowContext("wf-context", "{\"customer\":{\"id\":\"c-1\"},\"priority\":true}",
        "com.example.OrderContext", Instant.parse("2026-01-03T09:00:00Z"), Instant.parse("2026-01-03T09:10:00Z"));

    var context = service.context("wf-context");

    assertThat(context).isPresent();
    assertThat(context.get().context()).isEqualTo(Map.of("customer", Map.of("id", "c-1"), "priority", true));
    assertThat(context.get().contextClassName()).isEqualTo("com.example.OrderContext");
  }

  @Test
  @DisplayName("metadata returns the deserialized workflow metadata when present")
  void metadataReturnsDeserializedWorkflowMetadata() {
    insertWorkflow("wf-metadata", "orders", "RUNNING", Instant.parse("2026-01-03T09:00:00Z"), Instant.parse("2026-01-03T09:10:00Z"));
    insertWorkflowMetadata("wf-metadata", "{\"tenant\":\"acme\",\"region\":\"eu\"}",
        Map.class.getName(), Instant.parse("2026-01-03T09:00:00Z"), Instant.parse("2026-01-03T09:10:00Z"));

    var metadata = service.metadata("wf-metadata");

    assertThat(metadata).isPresent();
    assertThat(metadata.get().values()).isEqualTo(Map.of("tenant", "acme", "region", "eu"));
    assertThat(metadata.get().metadataClassName()).isEqualTo(Map.class.getName());
  }

  @Test
  @DisplayName("workflow, step, context, and metadata log readers return their audit rows")
  void logReadersReturnAuditRows() {
    insertWorkflowWithStep("wf-logs", "review");
    insertWorkflowLog("wf-logs", "ADMIN_UPDATE", "{\"status\":\"RUNNING\"}", Instant.parse("2026-01-04T10:00:00Z"));
    insertStepLog("wf-logs", "review", "FAILED", "{\"state\":\"FAILED\"}", Instant.parse("2026-01-04T10:01:00Z"));
    insertContextLog("wf-logs", "ADMIN_UPDATE", "{\"attempt\":2}", Instant.parse("2026-01-04T10:02:00Z"));
    insertMetadataLog("wf-logs", "ADMIN_UPDATE", "{\"tenant\":\"acme\"}", Instant.parse("2026-01-04T10:03:00Z"));

    assertThat(service.workflowLogs("wf-logs")).singleElement().satisfies(log -> {
      assertThat(log.action()).isEqualTo("ADMIN_UPDATE");
      assertThat(log.snapshotJson()).isEqualTo("{\"status\":\"RUNNING\"}");
    });
    assertThat(service.stepLogs("wf-logs", "review")).singleElement().satisfies(log -> {
      assertThat(log.action()).isEqualTo("FAILED");
      assertThat(log.stepName()).isEqualTo("review");
    });
    assertThat(service.contextLogs("wf-logs")).singleElement().satisfies(log ->
        assertThat(log.snapshotJson()).isEqualTo("{\"attempt\":2}"));
    assertThat(service.metadataLogs("wf-logs")).singleElement().satisfies(log ->
        assertThat(log.snapshotJson()).isEqualTo("{\"tenant\":\"acme\"}"));
  }

  @Test
  @DisplayName("search returns paginated workflow summaries with the latest current step")
  void searchReturnsPaginatedWorkflowSummaries() {
    insertWorkflow("wf-late", "orders", "RUNNING", Instant.parse("2026-01-05T09:00:00Z"), Instant.parse("2026-01-05T09:30:00Z"));
    insertWorkflow("wf-early", "payments", "SUCCESS", Instant.parse("2026-01-04T09:00:00Z"), Instant.parse("2026-01-04T09:30:00Z"));
    insertStep("wf-late", "orders", "reserve", "SUCCESS", 0,
        Instant.parse("2026-01-05T09:05:00Z"), Instant.parse("2026-01-05T09:10:00Z"), Instant.parse("2026-01-05T09:10:00Z"));
    insertStep("wf-late", "orders", "review", "SUSPENDED", 1,
        Instant.parse("2026-01-05T09:15:00Z"), null, Instant.parse("2026-01-05T09:20:00Z"));
    insertStep("wf-early", "payments", "charge", "SUCCESS", 0,
        Instant.parse("2026-01-04T09:05:00Z"), Instant.parse("2026-01-04T09:15:00Z"), Instant.parse("2026-01-04T09:15:00Z"));

    var result = service.search(new WorkflowQuery(null, null, null, null, null, null, null, null, null, 0, 1));

    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.totalPages()).isEqualTo(2);
    assertThat(result.content()).singleElement().satisfies(summary -> {
      assertThat(summary.workflowId()).isEqualTo("wf-late");
      assertThat(summary.currentStep()).isEqualTo("review");
    });
  }

  @Test
  @DisplayName("search applies workflow, step, metadata, and creation-range filters together")
  void searchAppliesCombinedFilters() {
    insertWorkflow("wf-match", "orders", "RUNNING", Instant.parse("2026-01-10T09:00:00Z"), Instant.parse("2026-01-10T09:10:00Z"));
    insertWorkflow("wf-other", "orders", "RUNNING", Instant.parse("2026-01-20T09:00:00Z"), Instant.parse("2026-01-20T09:10:00Z"));
    insertStep("wf-match", "orders", "review", "SUSPENDED", 0,
        Instant.parse("2026-01-10T09:05:00Z"), null, Instant.parse("2026-01-10T09:06:00Z"));
    insertStep("wf-other", "orders", "review", "SUCCESS", 0,
        Instant.parse("2026-01-20T09:05:00Z"), Instant.parse("2026-01-20T09:06:00Z"), Instant.parse("2026-01-20T09:06:00Z"));
    insertWorkflowMetadata("wf-match", "{\"tenant\":\"acme\"}", Map.class.getName(),
        Instant.parse("2026-01-10T09:00:00Z"), Instant.parse("2026-01-10T09:10:00Z"));
    insertWorkflowMetadata("wf-other", "{\"tenant\":\"other\"}", Map.class.getName(),
        Instant.parse("2026-01-20T09:00:00Z"), Instant.parse("2026-01-20T09:10:00Z"));

    var result = service.search(new WorkflowQuery(
        null,
        "orders",
        "RUNNING",
        "review",
        "SUSPENDED",
        "tenant",
        "acme",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-15T00:00:00Z"),
        0,
        25));

    assertThat(result.content()).singleElement().satisfies(summary ->
        assertThat(summary.workflowId()).isEqualTo("wf-match"));
  }

  private void insertWorkflowWithStep(String workflowId, String stepName) {
    insertWorkflow(workflowId, "demo-workflow", "SUSPENDED", Instant.parse("2026-01-01T09:00:00Z"), Instant.parse("2026-01-01T09:00:00Z"));
    insertStep(workflowId, "demo-workflow", stepName, "SUSPENDED", 0,
        Instant.parse("2026-01-01T09:00:00Z"), null, Instant.parse("2026-01-01T09:00:00Z"));
  }

  private void insertWorkflow(String workflowId, String workflow, String status, Instant createdAt, Instant updatedAt) {
    jdbc.update("""
            insert into workflow (pipeline_id, workflow, status, date_created, date_updated)
            values (:id, :workflow, :status, cast(:created as timestamptz), cast(:updated as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("workflow", workflow, Types.VARCHAR)
            .addValue("status", status, Types.VARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("updated", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertStep(String workflowId, String workflow, String stepName, String state, int retryCount,
                          Instant startedAt, Instant endedAt, Instant updatedAt) {
    jdbc.update("""
            insert into workflow_step
                (pipeline_id, workflow, step_name, step_type_class_name, state, retry_count, date_started, date_ended, date_updated)
            values (:id, :workflow, :step, :type, :state, :retryCount, cast(:started as timestamptz), cast(:ended as timestamptz), cast(:updated as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("workflow", workflow, Types.VARCHAR)
            .addValue("step", stepName, Types.VARCHAR)
            .addValue("type", "com.github.orcas.demo.Step", Types.VARCHAR)
            .addValue("state", state, Types.VARCHAR)
            .addValue("retryCount", retryCount, Types.INTEGER)
            .addValue("started", OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("ended", endedAt == null ? null : OffsetDateTime.ofInstant(endedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("updated", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertStepContext(String workflowId, String stepName, String parentStepName,
                                 String inputJson, String outputJson, String attributesJson, Instant updatedAt) {
    jdbc.update("""
            insert into workflow_step_context (pipeline_id, step_name, parent_step_name, input_json, output_json, attributes_json, date_updated)
            values (:id, :step, :parent, :input, :output, :attributes, cast(:updated as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("step", stepName, Types.VARCHAR)
            .addValue("parent", parentStepName, Types.VARCHAR)
            .addValue("input", inputJson, Types.LONGVARCHAR)
            .addValue("output", outputJson, Types.LONGVARCHAR)
            .addValue("attributes", attributesJson, Types.LONGVARCHAR)
            .addValue("updated", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertWorkflowContext(String workflowId, String contextJson, String contextClassName, Instant createdAt, Instant updatedAt) {
    jdbc.update("""
            insert into workflow_context (pipeline_id, context_json, context_class_name, date_created, date_updated)
            values (:id, :json, :className, cast(:created as timestamptz), cast(:updated as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("json", contextJson, Types.LONGVARCHAR)
            .addValue("className", contextClassName, Types.VARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("updated", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertWorkflowMetadata(String workflowId, String metadataJson, String metadataClassName, Instant createdAt, Instant updatedAt) {
    jdbc.update("""
            insert into workflow_metadata (pipeline_id, metadata_json, metadata_class_name, date_created, date_updated)
            values (:id, :json, :className, cast(:created as timestamptz), cast(:updated as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("json", metadataJson, Types.LONGVARCHAR)
            .addValue("className", metadataClassName, Types.VARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("updated", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertWorkflowLog(String workflowId, String action, String snapshotJson, Instant at) {
    jdbc.update("""
            insert into workflow_log (pipeline_id, action, snapshot_json, date_created)
            values (:id, :action, :snapshot, cast(:created as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("action", action, Types.VARCHAR)
            .addValue("snapshot", snapshotJson, Types.LONGVARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(at, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertStepLog(String workflowId, String stepName, String action, String snapshotJson, Instant at) {
    jdbc.update("""
            insert into workflow_step_log (pipeline_id, step_name, action, snapshot_json, date_created)
            values (:id, :step, :action, :snapshot, cast(:created as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("step", stepName, Types.VARCHAR)
            .addValue("action", action, Types.VARCHAR)
            .addValue("snapshot", snapshotJson, Types.LONGVARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(at, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertContextLog(String workflowId, String action, String snapshotJson, Instant at) {
    jdbc.update("""
            insert into workflow_context_log (pipeline_id, action, snapshot_json, date_created)
            values (:id, :action, :snapshot, cast(:created as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("action", action, Types.VARCHAR)
            .addValue("snapshot", snapshotJson, Types.LONGVARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(at, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }

  private void insertMetadataLog(String workflowId, String action, String snapshotJson, Instant at) {
    jdbc.update("""
            insert into workflow_metadata_log (pipeline_id, action, snapshot_json, date_created)
            values (:id, :action, :snapshot, cast(:created as timestamptz))
            """,
        new MapSqlParameterSource()
            .addValue("id", workflowId, Types.VARCHAR)
            .addValue("action", action, Types.VARCHAR)
            .addValue("snapshot", snapshotJson, Types.LONGVARCHAR)
            .addValue("created", OffsetDateTime.ofInstant(at, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
  }
}

