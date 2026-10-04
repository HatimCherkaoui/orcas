package com.github.orcas.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class WorkflowServiceControllerTest {

    @Test
    @DisplayName("replay without a body only triggers step replay")
    void replayWithoutBodySkipsContextUpdate() {
        var admin = new RecordingAdminService();
        var controller = new WorkflowServiceController(new NoopQueryService(), admin);

        var response = controller.replay("wf-1", "review", null);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(admin.calls).containsExactly("replayStep:wf-1:review");
        assertThat(admin.lastContext).isNull();
    }

    @Test
    @DisplayName("replay with a body updates context before replaying the step")
    void replayWithBodyUpdatesContextFirst() {
        var admin = new RecordingAdminService();
        var controller = new WorkflowServiceController(new NoopQueryService(), admin);

        var body = Map.of("reason", "retry-after-fix", "attempt", 2);
        var response = controller.replay("wf-2", "process", body);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(admin.calls).containsExactly(
                "replaceContext:wf-2",
                "replayStep:wf-2:process");
        assertThat(admin.lastContext).isEqualTo(body);
    }

    @Test
    @DisplayName("replays delegates to the query service")
    void replaysDelegatesToQueryService() {
        var replays = List.of(new ReplayView(7L, "wf-3", "review", "{\"stepName\":\"review\"}", Instant.parse("2026-01-01T10:00:00Z")));
        var controller = new WorkflowServiceController(new NoopQueryService(replays), new RecordingAdminService());

        assertThat(controller.replays("wf-3")).isEqualTo(replays);
    }

    private static final class RecordingAdminService implements WorkflowAdminService {
        private final List<String> calls = new ArrayList<>();
        private Object lastContext;

        @Override
        public void updateWorkflowStatus(String workflowId, String status) {
            calls.add("updateWorkflowStatus:" + workflowId + ':' + status);
        }

        @Override
        public void updateStepState(String workflowId, String stepName, String state) {
            calls.add("updateStepState:" + workflowId + ':' + stepName + ':' + state);
        }

        @Override
        public void replaceContext(String workflowId, Object context) {
            calls.add("replaceContext:" + workflowId);
            lastContext = context;
        }

        @Override
        public void replaceMetadata(String workflowId, Map<String, String> metadata) {
            calls.add("replaceMetadata:" + workflowId);
        }

        @Override
        public void abandonWorkflow(String workflowId, String reason) {
            calls.add("abandonWorkflow:" + workflowId + ':' + reason);
        }

        @Override
        public void replayStep(String workflowId, String stepName) {
            calls.add("replayStep:" + workflowId + ':' + stepName);
        }

        @Override
        public BatchReplayResult replaySuspendedSteps(WorkflowQuery query) {
            calls.add("replaySuspendedSteps");
            return new BatchReplayResult(0, 0, 0);
        }
    }

    private static final class NoopQueryService implements WorkflowQueryService {
        private final List<ReplayView> replays;

        private NoopQueryService() {
            this(List.of());
        }

        private NoopQueryService(List<ReplayView> replays) {
            this.replays = replays;
        }

        @Override public PageResult<WorkflowSummary> search(WorkflowQuery query) { return new PageResult<>(List.of(), 0, 0, 25, 0); }
        @Override public Optional<WorkflowDetails> find(String workflowId) { return Optional.empty(); }
        @Override public List<WorkflowStepView> steps(String workflowId) { return List.of(); }
        @Override public WorkflowStepView step(String workflowId, String stepName) { throw new UnsupportedOperationException(); }
        @Override public Optional<StepContext> stepContext(String workflowId, String stepName) { return Optional.empty(); }
        @Override public Optional<ContextView> context(String workflowId) { return Optional.empty(); }
        @Override public Optional<MetadataView> metadata(String workflowId) { return Optional.empty(); }
        @Override public List<EntityLogView> workflowLogs(String workflowId) { return List.of(); }
        @Override public List<EntityLogView> stepLogs(String workflowId, String stepName) { return List.of(); }
        @Override public List<EntityLogView> contextLogs(String workflowId) { return List.of(); }
        @Override public List<EntityLogView> metadataLogs(String workflowId) { return List.of(); }
        @Override public List<ReplayView> replays(String workflowId) { return replays; }
    }
}
