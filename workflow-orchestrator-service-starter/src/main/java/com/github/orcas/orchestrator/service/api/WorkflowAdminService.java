package com.github.orcas.orchestrator.service.api;

import java.util.Map;

/**
 * Mutating operations available to dashboard operators for correcting or replaying
 * workflow executions. Every call here represents a manual override of state normally
 * driven by {@code WorkflowEngine} itself, so implementations (see
 * {@code JdbcWorkflowAdminService}) are expected to be audited - the REST layer
 * (see {@code WorkflowServiceController}) logs an INFO entry for each of these calls.
 */
public interface WorkflowAdminService {
    /** Forces a workflow instance's overall status, bypassing normal engine transitions. */
    void updateWorkflowStatus(String pipelineId, String status);

    /** Forces a single step execution's recorded state. */
    void updateStepState(String pipelineId, String stepName, String state);

    /** Overwrites the business context stored for a workflow instance. */
    void replaceContext(String pipelineId, Object context);

    /** Overwrites the technical metadata key/value map stored for a workflow instance. */
    void replaceMetadata(String pipelineId, Map<String, String> metadata);

    /** Re-executes a previously failed or suspended step from its last known input. */
    void replayStep(String pipelineId, String stepName);
}
