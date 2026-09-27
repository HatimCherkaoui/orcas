package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowServiceControllerTest {
    @Test
    void returnsRegisteredWorkflowDefinition() {
        var controller = new WorkflowServiceController(new NoopQuery(), new NoopAdmin());

        var response = controller.definition("orders");

        assertThat(response.workflow()).isEqualTo("orders");
    }

    private static final class NoopQuery implements WorkflowQueryService {
        public PageResult<WorkflowSummary> search(WorkflowQuery query) {
            return new PageResult<>(List.of(), 0, 0, 25, 0);
        }
        public Optional<WorkflowDetails> find(String workflowId) { return Optional.empty(); }
        public List<WorkflowStepView> steps(String workflowId) { return List.of(); }
        public WorkflowStepView step(String workflowId, String stepName) { throw new UnsupportedOperationException(); }
        public Optional<com.github.orcas.orchestrator.core.model.StepContext> stepContext(
                String workflowId,
                String stepName) {
            return Optional.empty();
        }
        public Optional<ContextView> context(String workflowId) { return Optional.empty(); }
        public Optional<MetadataView> metadata(String workflowId) { return Optional.empty(); }
        public List<EntityLogView> workflowLogs(String workflowId) { return List.of(); }
        public List<EntityLogView> stepLogs(String workflowId, String stepName) { return List.of(); }
        public List<EntityLogView> contextLogs(String workflowId) { return List.of(); }
        public List<EntityLogView> metadataLogs(String workflowId) { return List.of(); }
    }

    private static final class NoopAdmin implements WorkflowAdminService {
        public void updateWorkflowStatus(String workflowId, String status) {}
        public void updateStepState(String workflowId, String stepName, String state) {}
        public void replaceContext(String workflowId, Object context) {}
        public void replaceMetadata(String workflowId, Map<String, String> metadata) {}
        public void replayStep(String workflowId, String stepName) {}
        public BatchReplayResult replaySuspendedSteps(WorkflowQueryService.WorkflowQuery query) {
            return new BatchReplayResult(0, 0, 0);
        }
    }
}
