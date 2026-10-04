package com.github.orcas.orchestrator.service.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.github.orcas.orchestrator.core.model.StepContext;

/**
 * Read-only query facade over persisted workflow execution state, powering the
 * dashboard's workflow list, detail, and audit-log views. Implementations (see
 * {@code JdbcWorkflowQueryService}) translate these calls into read queries against
 * the configured workflow state store; no mutation happens through this interface
 * (see {@link WorkflowAdminService} for that).
 */
public interface WorkflowQueryService {
    /** Searches workflow instances matching {@code query}, paginated per its {@code page}/{@code size}. */
    PageResult<WorkflowSummary> search(WorkflowQuery query);

    /** Looks up a single workflow instance by its instance id. */
    Optional<WorkflowDetails> find(String workflowId);

    /** Lists every recorded step execution for a workflow instance, in execution order. */
    List<WorkflowStepView> steps(String workflowId);

    /** Looks up a single recorded step execution by name. */
    WorkflowStepView step(String workflowId, String stepName);

    /** Loads the resolved input/output/attributes context captured for a given step execution. */
    Optional<StepContext> stepContext(String workflowId, String stepName);

    /** Loads the original business context supplied when the workflow was launched. */
    Optional<ContextView> context(String workflowId);

    /** Loads the technical key/value metadata associated with a workflow instance. */
    Optional<MetadataView> metadata(String workflowId);

    /** Lists the audit log entries recorded for the workflow instance itself (status changes). */
    List<EntityLogView> workflowLogs(String workflowId);

    /** Lists the audit log entries recorded for a specific step (state transitions, replays). */
    List<EntityLogView> stepLogs(String workflowId, String stepName);

    /** Lists the audit log entries recorded for context updates on the workflow instance. */
    List<EntityLogView> contextLogs(String workflowId);

    /** Lists the audit log entries recorded for metadata updates on the workflow instance. */
    List<EntityLogView> metadataLogs(String workflowId);

    /** Lists replay requests recorded for the workflow instance, newest first. */
    List<ReplayView> replays(String workflowId);

    /** Search criteria and pagination parameters accepted by {@link #search(WorkflowQuery)}. */
    record WorkflowQuery(
            String workflowId,
            String workflow,
            String status,
            String stepName,
            String stepStatus,
            String metadataKey,
            String metadataValue,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        public WorkflowQuery {
            page = Math.max(page, 0);
            size = Math.min(Math.max(size, 1), 200);
        }
    }

    /** A single page of {@code content}, alongside the total element/page counts. */
    record PageResult<T>(List<T> content, long totalElements, int page, int size, int totalPages) {
        public PageResult {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    /** Lightweight summary of a workflow instance, used in list/search views. */
    record WorkflowSummary(String workflowId, String workflow, String status,
                           Instant dateCreated, Instant dateUpdated, String currentStep) {
    }

    /** Full details of a single workflow instance. */
    record WorkflowDetails(String workflowId, String workflow, String status,
                           Instant dateCreated, Instant dateUpdated) {
    }

    /** A single recorded step execution and its lifecycle timestamps. */
    record WorkflowStepView(String workflowId, String workflow, String stepName,
                            String typeClassName, String state, int retryCount, Instant dateStarted,
                            Instant dateEnded, Instant dateUpdated) {
    }

    /** The business context captured for a workflow instance. */
    record ContextView(String workflowId, String contextClassName, Object context,
                       Instant dateCreated, Instant dateUpdated) {
    }

    /** The technical metadata key/value map captured for a workflow instance. */
    record MetadataView(String workflowId, String metadataClassName, Map<String, String> values,
                        Instant dateCreated, Instant dateUpdated) {
        public MetadataView {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    /** A single replay request recorded for a workflow step. */
    record ReplayView(long id, String workflowId, String stepName, String snapshotJson, Instant dateCreated) {
    }

    /** A single audit log entry describing an action taken on a workflow instance or step. */
    record EntityLogView(long id, String workflowId, String stepName, String action,
                         String snapshotJson, Instant dateCreated) {
    }
}
