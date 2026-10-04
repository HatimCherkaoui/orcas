package com.github.orcas.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.ContextView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.EntityLogView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.MetadataView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.PageResult;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.ReplayView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowDetails;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowStepView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class WorkflowServiceControllerIntegrationTest {
  private MockMvc mvc;
  private WorkflowQueryService query;
  private WorkflowAdminService admin;

  @BeforeEach
  void setUp() {
    query = mock(WorkflowQueryService.class);
    admin = mock(WorkflowAdminService.class);
    mvc = MockMvcBuilders.standaloneSetup(new WorkflowServiceController(query, admin))
        .addPlaceholderValue("workflow.orchestrator.service.base-path", "/api/orchestrator")
        .build();
  }

  @Test
  @DisplayName("POST replay without a body returns 202 and only replays the step")
  void replayWithoutBodyReturnsAccepted() throws Exception {
    mvc.perform(post("/api/orchestrator/workflows/wf-1/steps/review/replay"))
        .andExpect(status().isAccepted());

    verify(admin).replayStep("wf-1", "review");
    verify(admin, never()).replaceContext(any(), any());
  }

  @Test
  @DisplayName("POST replay with a JSON body updates context before replaying")
  void replayWithBodyUpdatesContextBeforeReplaying() throws Exception {
    mvc.perform(post("/api/orchestrator/workflows/wf-2/steps/process/replay")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"reason":"retry-after-fix","attempt":2}
                    """))
        .andExpect(status().isAccepted());

    ArgumentCaptor<Object> contextCaptor = ArgumentCaptor.forClass(Object.class);
    InOrder callOrder = inOrder(admin);
    callOrder.verify(admin).replaceContext(eq("wf-2"), contextCaptor.capture());
    callOrder.verify(admin).replayStep("wf-2", "process");
    assertThat(contextCaptor.getValue()).isEqualTo(Map.of("reason", "retry-after-fix", "attempt", 2));
  }

  @Test
  @DisplayName("GET replays returns replay history as JSON")
  void replaysReturnsJsonPayload() throws Exception {
    when(query.replays("wf-3")).thenReturn(List.of(
        new ReplayView(7L, "wf-3", "review", "{\"stepName\":\"review\"}", Instant.parse("2026-01-01T10:00:00Z")),
        new ReplayView(8L, "wf-3", "settle", "{\"stepName\":\"settle\"}", Instant.parse("2026-01-01T10:05:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-3/replays"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value(7))
        .andExpect(jsonPath("$[0].workflowId").value("wf-3"))
        .andExpect(jsonPath("$[0].stepName").value("review"))
        .andExpect(jsonPath("$[0].snapshotJson").value("{\"stepName\":\"review\"}"))
        .andExpect(jsonPath("$[0].dateCreated").value("2026-01-01T10:00:00Z"))
        .andExpect(jsonPath("$[1].id").value(8))
        .andExpect(jsonPath("$[1].stepName").value("settle"));

    verify(query).replays("wf-3");
  }

  @Test
  @DisplayName("GET workflows binds filters, normalizes pagination, and returns page JSON")
  void searchBindsFiltersAndNormalizesPagination() throws Exception {
    when(query.search(any())).thenReturn(new PageResult<>(List.of(
        new WorkflowSummary(
            "wf-search",
            "orders",
            "RUNNING",
            Instant.parse("2026-01-02T10:15:30Z"),
            Instant.parse("2026-01-02T10:20:30Z"),
            "review")), 1, 0, 200, 1));

    mvc.perform(get("/api/orchestrator/workflows")
            .queryParam("workflowId", "wf-search")
            .queryParam("workflow", "orders")
            .queryParam("status", "RUNNING")
            .queryParam("stepName", "review")
            .queryParam("stepStatus", "SUSPENDED")
            .queryParam("metadataKey", "tenant")
            .queryParam("metadataValue", "acme")
            .queryParam("createdFrom", "2026-01-01T00:00:00Z")
            .queryParam("createdTo", "2026-01-31T23:59:59Z")
            .queryParam("page", "-1")
            .queryParam("size", "500"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(200))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.content[0].workflowId").value("wf-search"))
        .andExpect(jsonPath("$.content[0].workflow").value("orders"))
        .andExpect(jsonPath("$.content[0].status").value("RUNNING"))
        .andExpect(jsonPath("$.content[0].currentStep").value("review"));

    ArgumentCaptor<WorkflowQuery> queryCaptor = ArgumentCaptor.forClass(WorkflowQuery.class);
    verify(query).search(queryCaptor.capture());
    assertThat(queryCaptor.getValue()).isEqualTo(new WorkflowQuery(
        "wf-search",
        "orders",
        "RUNNING",
        "review",
        "SUSPENDED",
        "tenant",
        "acme",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-31T23:59:59Z"),
        0,
        200));
  }

  @Test
  @DisplayName("GET workflows returns 400 when createdFrom is not a valid instant")
  void searchRejectsMalformedInstant() throws Exception {
    mvc.perform(get("/api/orchestrator/workflows")
            .queryParam("createdFrom", "not-an-instant"))
        .andExpect(status().isBadRequest());

    verify(query, never()).search(any());
  }

  @Test
  @DisplayName("GET workflow by id returns details as JSON when present")
  void findReturnsWorkflowDetails() throws Exception {
    when(query.find("wf-10")).thenReturn(Optional.of(new WorkflowDetails(
        "wf-10",
        "orders",
        "SUCCESS",
        Instant.parse("2026-01-01T08:00:00Z"),
        Instant.parse("2026-01-01T08:05:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-10"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.workflowId").value("wf-10"))
        .andExpect(jsonPath("$.workflow").value("orders"))
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(query).find("wf-10");
  }

  @Test
  @DisplayName("GET workflow by id returns 404 when absent")
  void findReturnsNotFoundWhenMissing() throws Exception {
    when(query.find("wf-missing")).thenReturn(Optional.empty());

    mvc.perform(get("/api/orchestrator/workflows/wf-missing"))
        .andExpect(status().isNotFound());

    verify(query).find("wf-missing");
  }

  @Test
  @DisplayName("GET step returns the step view as JSON")
  void stepReturnsStepJson() throws Exception {
    when(query.step("wf-11", "review")).thenReturn(new WorkflowStepView(
        "wf-11",
        "orders",
        "review",
        "com.example.ReviewStep",
        "SUSPENDED",
        2,
        Instant.parse("2026-01-03T09:00:00Z"),
        null,
        Instant.parse("2026-01-03T09:15:00Z")));

    mvc.perform(get("/api/orchestrator/workflows/wf-11/steps/review"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.workflowId").value("wf-11"))
        .andExpect(jsonPath("$.stepName").value("review"))
        .andExpect(jsonPath("$.state").value("SUSPENDED"))
        .andExpect(jsonPath("$.retryCount").value(2))
        .andExpect(jsonPath("$.dateEnded").doesNotExist());

    verify(query).step("wf-11", "review");
  }

  @Test
  @DisplayName("GET steps returns the recorded step list as JSON")
  void stepsReturnsJsonArray() throws Exception {
    when(query.steps("wf-steps")).thenReturn(List.of(
        new WorkflowStepView(
            "wf-steps",
            "orders",
            "review",
            "com.example.ReviewStep",
            "SUSPENDED",
            1,
            Instant.parse("2026-01-05T09:00:00Z"),
            null,
            Instant.parse("2026-01-05T09:15:00Z")),
        new WorkflowStepView(
            "wf-steps",
            "orders",
            "settle",
            "com.example.SettleStep",
            "SUCCESS",
            0,
            Instant.parse("2026-01-05T09:16:00Z"),
            Instant.parse("2026-01-05T09:20:00Z"),
            Instant.parse("2026-01-05T09:20:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-steps/steps"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].workflowId").value("wf-steps"))
        .andExpect(jsonPath("$[0].workflow").value("orders"))
        .andExpect(jsonPath("$[0].stepName").value("review"))
        .andExpect(jsonPath("$[0].typeClassName").value("com.example.ReviewStep"))
        .andExpect(jsonPath("$[0].state").value("SUSPENDED"))
        .andExpect(jsonPath("$[0].retryCount").value(1))
        .andExpect(jsonPath("$[0].dateStarted").value("2026-01-05T09:00:00Z"))
        .andExpect(jsonPath("$[0].dateUpdated").value("2026-01-05T09:15:00Z"))
        .andExpect(jsonPath("$[0].dateEnded").doesNotExist())
        .andExpect(jsonPath("$[1].stepName").value("settle"))
        .andExpect(jsonPath("$[1].dateEnded").value("2026-01-05T09:20:00Z"));

    verify(query).steps("wf-steps");
  }

  @Test
  @DisplayName("GET steps returns an empty JSON array when the workflow has no recorded steps")
  void stepsReturnsEmptyArrayWhenNoStepsExist() throws Exception {
    when(query.steps("wf-empty")).thenReturn(List.of());

    mvc.perform(get("/api/orchestrator/workflows/wf-empty/steps"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json("[]"));

    verify(query).steps("wf-empty");
  }

  @Test
  @DisplayName("GET step returns 404 when the step lookup is empty")
  void stepReturnsNotFoundWhenMissing() throws Exception {
    when(query.step("wf-12", "missing")).thenThrow(new EmptyResultDataAccessException(1));

    mvc.perform(get("/api/orchestrator/workflows/wf-12/steps/missing"))
        .andExpect(status().isNotFound());

    verify(query).step("wf-12", "missing");
  }

  @Test
  @DisplayName("GET step context returns the stored step context when present")
  void stepContextReturnsJson() throws Exception {
    when(query.stepContext("wf-ctx", "review")).thenReturn(Optional.of(new StepContext(
        "wf-ctx",
        "orders",
        "review",
        "init",
        Map.of("amount", 42, "currency", "EUR"),
        Map.of("approved", true),
        Map.of("attempt", 2, "note", "retry"),
        Instant.parse("2026-01-06T09:30:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-ctx/steps/review/context"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.workflowId").value("wf-ctx"))
        .andExpect(jsonPath("$.stepName").value("review"))
        .andExpect(jsonPath("$.parentStepName").value("init"))
        .andExpect(jsonPath("$.input.amount").value(42))
        .andExpect(jsonPath("$.output.approved").value(true))
        .andExpect(jsonPath("$.attributes.attempt").value(2));

    verify(query).stepContext("wf-ctx", "review");
  }

  @Test
  @DisplayName("GET step context returns 404 when no step context exists")
  void stepContextReturnsNotFoundWhenMissing() throws Exception {
    when(query.stepContext("wf-ctx-missing", "review")).thenReturn(Optional.empty());

    mvc.perform(get("/api/orchestrator/workflows/wf-ctx-missing/steps/review/context"))
        .andExpect(status().isNotFound());

    verify(query).stepContext("wf-ctx-missing", "review");
  }

  @Test
  @DisplayName("GET context returns nested context JSON when present")
  void contextReturnsNestedJson() throws Exception {
    when(query.context("wf-13")).thenReturn(Optional.of(new ContextView(
        "wf-13",
        "com.example.OrderContext",
        Map.of(
            "attempt", 3,
            "flags", List.of("urgent", "vip"),
            "customer", Map.of("id", "c-1", "active", true)),
        Instant.parse("2026-01-04T10:00:00Z"),
        Instant.parse("2026-01-04T10:10:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-13/context"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.workflowId").value("wf-13"))
        .andExpect(jsonPath("$.contextClassName").value("com.example.OrderContext"))
        .andExpect(jsonPath("$.context.attempt").value(3))
        .andExpect(jsonPath("$.context.flags[0]").value("urgent"))
        .andExpect(jsonPath("$.context.customer.active").value(true));

    verify(query).context("wf-13");
  }

  @Test
  @DisplayName("GET context returns 404 when absent")
  void contextReturnsNotFoundWhenMissing() throws Exception {
    when(query.context("wf-14")).thenReturn(Optional.empty());

    mvc.perform(get("/api/orchestrator/workflows/wf-14/context"))
        .andExpect(status().isNotFound());

    verify(query).context("wf-14");
  }

  @Test
  @DisplayName("GET metadata returns metadata values as JSON object")
  void metadataReturnsJson() throws Exception {
    when(query.metadata("wf-15")).thenReturn(Optional.of(new MetadataView(
        "wf-15",
        "java.util.Map",
        Map.of("tenant", "acme", "region", "eu-west-1"),
        Instant.parse("2026-01-04T11:00:00Z"),
        Instant.parse("2026-01-04T11:10:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-15/metadata"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.workflowId").value("wf-15"))
        .andExpect(jsonPath("$.metadataClassName").value("java.util.Map"))
        .andExpect(jsonPath("$.values.tenant").value("acme"))
        .andExpect(jsonPath("$.values.region").value("eu-west-1"));

    verify(query).metadata("wf-15");
  }

  @Test
  @DisplayName("GET metadata returns 404 when absent")
  void metadataReturnsNotFoundWhenMissing() throws Exception {
    when(query.metadata("wf-16")).thenReturn(Optional.empty());

    mvc.perform(get("/api/orchestrator/workflows/wf-16/metadata"))
        .andExpect(status().isNotFound());

    verify(query).metadata("wf-16");
  }

  @Test
  @DisplayName("GET workflow logs returns the workflow audit log entries as JSON")
  void workflowLogsReturnsJson() throws Exception {
    when(query.workflowLogs("wf-logs")).thenReturn(List.of(
        new EntityLogView(1L, "wf-logs", null, "ADMIN_UPDATE", "{\"status\":\"RUNNING\"}", Instant.parse("2026-01-07T10:00:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-logs/logs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].workflowId").value("wf-logs"))
        .andExpect(jsonPath("$[0].stepName").doesNotExist())
        .andExpect(jsonPath("$[0].action").value("ADMIN_UPDATE"));

    verify(query).workflowLogs("wf-logs");
  }

  @Test
  @DisplayName("GET step logs returns the step audit log entries as JSON")
  void stepLogsReturnsJson() throws Exception {
    when(query.stepLogs("wf-step-log", "review")).thenReturn(List.of(
        new EntityLogView(2L, "wf-step-log", "review", "FAILED", "{\"state\":\"FAILED\"}", Instant.parse("2026-01-07T10:05:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-step-log/steps/review/logs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].stepName").value("review"))
        .andExpect(jsonPath("$[0].action").value("FAILED"));

    verify(query).stepLogs("wf-step-log", "review");
  }

  @Test
  @DisplayName("GET context logs returns the context audit log entries as JSON")
  void contextLogsReturnsJson() throws Exception {
    when(query.contextLogs("wf-context-log")).thenReturn(List.of(
        new EntityLogView(3L, "wf-context-log", null, "ADMIN_UPDATE", "{\"attempt\":2}", Instant.parse("2026-01-07T10:10:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-context-log/context/logs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].workflowId").value("wf-context-log"))
        .andExpect(jsonPath("$[0].action").value("ADMIN_UPDATE"));

    verify(query).contextLogs("wf-context-log");
  }

  @Test
  @DisplayName("GET metadata logs returns the metadata audit log entries as JSON")
  void metadataLogsReturnsJson() throws Exception {
    when(query.metadataLogs("wf-meta-log")).thenReturn(List.of(
        new EntityLogView(4L, "wf-meta-log", null, "ADMIN_UPDATE", "{\"tenant\":\"acme\"}", Instant.parse("2026-01-07T10:15:00Z"))));

    mvc.perform(get("/api/orchestrator/workflows/wf-meta-log/metadata/logs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].workflowId").value("wf-meta-log"))
        .andExpect(jsonPath("$[0].action").value("ADMIN_UPDATE"));

    verify(query).metadataLogs("wf-meta-log");
  }

  @Test
  @DisplayName("GET definitions returns an empty workflow graph for the requested workflow")
  void definitionReturnsEmptyWorkflowGraph() throws Exception {
    mvc.perform(get("/api/orchestrator/workflows/definitions/orders"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.workflow").value("orders"))
        .andExpect(jsonPath("$.steps").isArray())
        .andExpect(jsonPath("$.steps").isEmpty())
        .andExpect(jsonPath("$.routes").isArray())
        .andExpect(jsonPath("$.routes").isEmpty());
  }

  @Test
  @DisplayName("PATCH workflow returns 204 and delegates to the admin service")
  void updateWorkflowReturnsNoContent() throws Exception {
    mvc.perform(patch("/api/orchestrator/workflows/wf-update")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"status":"FAILED"}
                    """))
        .andExpect(status().isNoContent());

    verify(admin).updateWorkflowStatus("wf-update", "FAILED");
  }

  @Test
  @DisplayName("PATCH workflow returns 404 when the workflow is missing")
  void updateWorkflowReturnsNotFoundWhenMissing() throws Exception {
    org.mockito.Mockito.doThrow(new EmptyResultDataAccessException(1))
        .when(admin).updateWorkflowStatus("wf-update-missing", "FAILED");

    mvc.perform(patch("/api/orchestrator/workflows/wf-update-missing")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"status":"FAILED"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH step returns 204 and delegates to the admin service")
  void updateStepReturnsNoContent() throws Exception {
    mvc.perform(patch("/api/orchestrator/workflows/wf-step-update/steps/review")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"state":"SUCCESS"}
                    """))
        .andExpect(status().isNoContent());

    verify(admin).updateStepState("wf-step-update", "review", "SUCCESS");
  }

  @Test
  @DisplayName("PATCH step returns 404 when the step is missing")
  void updateStepReturnsNotFoundWhenMissing() throws Exception {
    org.mockito.Mockito.doThrow(new EmptyResultDataAccessException(1))
        .when(admin).updateStepState("wf-step-missing", "review", "FAILED");

    mvc.perform(patch("/api/orchestrator/workflows/wf-step-missing/steps/review")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"state":"FAILED"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT context returns 204 and sends the request body to the admin service")
  void replaceContextReturnsNoContent() throws Exception {
    mvc.perform(put("/api/orchestrator/workflows/wf-context-update/context")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"orderId":"o-1","attempt":3,"flags":["retry"]}
                    """))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Object> contextCaptor = ArgumentCaptor.forClass(Object.class);
    verify(admin).replaceContext(eq("wf-context-update"), contextCaptor.capture());
    assertThat(contextCaptor.getValue()).isEqualTo(Map.of("orderId", "o-1", "attempt", 3, "flags", List.of("retry")));
  }

  @Test
  @DisplayName("PUT context returns 404 when the workflow context row is missing")
  void replaceContextReturnsNotFoundWhenMissing() throws Exception {
    org.mockito.Mockito.doThrow(new EmptyResultDataAccessException(1))
        .when(admin).replaceContext(eq("wf-context-missing"), any());

    mvc.perform(put("/api/orchestrator/workflows/wf-context-missing/context")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"attempt":9}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT metadata returns 204 and sends metadata to the admin service")
  void replaceMetadataReturnsNoContent() throws Exception {
    mvc.perform(put("/api/orchestrator/workflows/wf-metadata-update/metadata")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"tenant":"acme","region":"eu-west-1"}
                    """))
        .andExpect(status().isNoContent());

    verify(admin).replaceMetadata("wf-metadata-update", Map.of("tenant", "acme", "region", "eu-west-1"));
  }

  @Test
  @DisplayName("PUT metadata returns 404 when the workflow metadata row is missing")
  void replaceMetadataReturnsNotFoundWhenMissing() throws Exception {
    org.mockito.Mockito.doThrow(new EmptyResultDataAccessException(1))
        .when(admin).replaceMetadata("wf-meta-missing", Map.of("tenant", "acme"));

    mvc.perform(put("/api/orchestrator/workflows/wf-meta-missing/metadata")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"tenant":"acme"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST abandon returns 202 and forwards the optional reason")
  void abandonReturnsAccepted() throws Exception {
    mvc.perform(post("/api/orchestrator/workflows/wf-abandon/abandon")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"reason":"operator-cancelled"}
                    """))
        .andExpect(status().isAccepted());

    verify(admin).abandonWorkflow("wf-abandon", "operator-cancelled");
  }

  @Test
  @DisplayName("POST abandon returns 202 with a null reason when the body is omitted")
  void abandonWithoutBodyUsesNullReason() throws Exception {
    mvc.perform(post("/api/orchestrator/workflows/wf-abandon-null/abandon"))
        .andExpect(status().isAccepted());

    verify(admin).abandonWorkflow("wf-abandon-null", null);
  }

  @Test
  @DisplayName("POST abandon returns 404 when the workflow is missing")
  void abandonReturnsNotFoundWhenMissing() throws Exception {
    org.mockito.Mockito.doThrow(new EmptyResultDataAccessException(1))
        .when(admin).abandonWorkflow("wf-abandon-missing", "operator-cancelled");

    mvc.perform(post("/api/orchestrator/workflows/wf-abandon-missing/abandon")
            .contentType(APPLICATION_JSON)
            .content("""
                    {"reason":"operator-cancelled"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST replay returns 404 when the step is missing during replay")
  void replayReturnsNotFoundWhenStepIsMissing() throws Exception {
    org.mockito.Mockito.doThrow(new EmptyResultDataAccessException(1))
        .when(admin).replayStep("wf-replay-missing", "review");

    mvc.perform(post("/api/orchestrator/workflows/wf-replay-missing/steps/review/replay"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST batch replay binds the request body, normalizes pagination, and returns batch counts")
  void replaySuspendedBindsBodyAndReturnsBatchResult() throws Exception {
    when(admin.replaySuspendedSteps(any()))
        .thenReturn(new WorkflowAdminService.BatchReplayResult(9, 7, 2));

    mvc.perform(post("/api/orchestrator/workflows/replay")
            .contentType(APPLICATION_JSON)
            .content("""
                    {
                      "workflowId": "wf-batch",
                      "workflow": "orders",
                      "status": "SUSPENDED",
                      "stepName": "review",
                      "stepStatus": "FAILED",
                      "metadataKey": "tenant",
                      "metadataValue": "acme",
                      "createdFrom": "2026-01-01T00:00:00Z",
                      "createdTo": "2026-01-31T23:59:59Z",
                      "page": -2,
                      "size": 500
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.matched").value(9))
        .andExpect(jsonPath("$.replayed").value(7))
        .andExpect(jsonPath("$.failed").value(2));

    ArgumentCaptor<WorkflowQuery> queryCaptor = ArgumentCaptor.forClass(WorkflowQuery.class);
    verify(admin).replaySuspendedSteps(queryCaptor.capture());
    assertThat(queryCaptor.getValue()).isEqualTo(new WorkflowQuery(
        "wf-batch",
        "orders",
        "SUSPENDED",
        "review",
        "FAILED",
        "tenant",
        "acme",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-31T23:59:59Z"),
        0,
        200));
  }

  @Test
  @DisplayName("POST batch replay returns 400 when the request body contains an invalid instant")
  void replaySuspendedRejectsMalformedInstant() throws Exception {
    mvc.perform(post("/api/orchestrator/workflows/replay")
            .contentType(APPLICATION_JSON)
            .content("""
                    {
                      "status": "SUSPENDED",
                      "createdFrom": "not-an-instant",
                      "page": 0,
                      "size": 25
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(admin, never()).replaySuspendedSteps(any());
  }
}






