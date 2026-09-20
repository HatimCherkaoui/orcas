package com.github.orcas.orchestrator.service.api;

import java.util.Map;

import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;

/**
 * Mutating operations available to dashboard operators for correcting or replaying
 * workflow executions. Every call here represents a manual override of state normally
 * driven by {@code WorkflowEngine} itself, so implementations (see
 * {@code JdbcWorkflowAdminService}) are expected to be audited - the REST layer
 * (see {@code WorkflowServiceController}) logs an INFO entry for each of these calls.
 */
public interface WorkflowAdminService {
    record BatchReplayResult(int matched, int replayed, int failed) {
    }

    /** Forces a workflow instance's overall status, bypassing normal engine transitions. */
    void updateWorkflowStatus(String workflowId, String status);

    /** Forces a single persisted step state, bypassing normal engine transitions. */
    void updateStepState(String workflowId, String stepName, String state);

    /** Replaces the stored workflow context JSON for a workflow instance. */
    void replaceContext(String workflowId, Object context);

    /** Replaces the stored workflow metadata JSON for a workflow instance. */
    void replaceMetadata(String workflowId, Map<String, String> metadata);

    /** Re-executes a previously failed or suspended step from its last known input. */
    void replayStep(String workflowId, String stepName);

    /** Replays every currently suspended step matching the provided workflow filters. */
    BatchReplayResult replaySuspendedSteps(WorkflowQuery query);
}
