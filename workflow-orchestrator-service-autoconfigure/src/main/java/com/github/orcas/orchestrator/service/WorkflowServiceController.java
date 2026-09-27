package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.ContextView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.EntityLogView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.MetadataView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.PageResult;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowDetails;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowStepView;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Operational API used by the dashboard and by operators. */
@RestController
@RequestMapping("${workflow.orchestrator.service.base-path:/api/orchestrator}/workflows")
public final class WorkflowServiceController {
    private final WorkflowQueryService query;
    private final WorkflowAdminService admin;
    public WorkflowServiceController(WorkflowQueryService query, WorkflowAdminService admin) {
        this.query = query;
        this.admin = admin;
    }

    @GetMapping
    public PageResult<WorkflowQueryService.WorkflowSummary> search(
            @RequestParam(name = "workflowId", required = false) String workflowId,
            @RequestParam(name = "workflow", required = false) String workflow,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "stepName", required = false) String stepName,
            @RequestParam(name = "stepStatus", required = false) String stepStatus,
            @RequestParam(name = "metadataKey", required = false) String metadataKey,
            @RequestParam(name = "metadataValue", required = false) String metadataValue,
            @RequestParam(name = "createdFrom", required = false) Instant createdFrom,
            @RequestParam(name = "createdTo", required = false) Instant createdTo,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "${workflow.orchestrator.service.default-page-size:25}") int size) {
        return query.search(new WorkflowQuery(workflowId, workflow, status, stepName, stepStatus,
                metadataKey, metadataValue, createdFrom, createdTo, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDetails> find(@PathVariable String id) { return ResponseEntity.of(query.find(id)); }
    @GetMapping("/{id}/steps")
    public List<WorkflowStepView> steps(@PathVariable String id) { return query.steps(id); }
    @GetMapping("/{id}/steps/{stepName}")
    public ResponseEntity<WorkflowStepView> step(@PathVariable String id, @PathVariable String stepName) {
        try { return ResponseEntity.ok(query.step(id, stepName)); }
        catch (EmptyResultDataAccessException e) { return ResponseEntity.notFound().build(); }
    }
    @GetMapping("/{id}/steps/{stepName}/context")
    public ResponseEntity<StepContext> stepContext(
            @PathVariable String id, @PathVariable String stepName) {
        return ResponseEntity.of(query.stepContext(id, stepName));
    }
    @GetMapping("/{id}/context")
    public ResponseEntity<ContextView> context(@PathVariable String id) { return ResponseEntity.of(query.context(id)); }
    @GetMapping("/{id}/metadata")
    public ResponseEntity<MetadataView> metadata(@PathVariable String id) { return ResponseEntity.of(query.metadata(id)); }
    @GetMapping("/{id}/logs")
    public List<EntityLogView> logs(@PathVariable String id) { return query.workflowLogs(id); }
    @GetMapping("/{id}/steps/{stepName}/logs")
    public List<EntityLogView> stepLogs(@PathVariable String id, @PathVariable String stepName) { return query.stepLogs(id, stepName); }
    @GetMapping("/{id}/context/logs")
    public List<EntityLogView> contextLogs(@PathVariable String id) { return query.contextLogs(id); }
    @GetMapping("/{id}/metadata/logs")
    public List<EntityLogView> metadataLogs(@PathVariable String id) { return query.metadataLogs(id); }
    /**
     * Management service deliberately does not load application workflow classes.
     * Runtime definitions live in the example/application service. The dashboard can
     * still render the persisted execution graph from /steps, so this endpoint returns
     * an empty routing definition instead of forcing the management service to depend on
     * application beans.
     */
    @GetMapping("/definitions/{workflow}")
    public WorkflowGraph definition(@PathVariable String workflow) {
        return new WorkflowGraph(workflow, List.of(), List.of());
    }
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateWorkflow(@PathVariable String id, @RequestBody Map<String, String> body) {
        admin.updateWorkflowStatus(id, body.get("status"));
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/steps/{stepName}")
    public ResponseEntity<Void> updateStep(@PathVariable String id, @PathVariable String stepName,
                                            @RequestBody Map<String, String> body) {
        admin.updateStepState(id, stepName, body.get("state"));
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/context")
    public ResponseEntity<Void> context(@PathVariable String id, @RequestBody Object body) {
        admin.replaceContext(id, body);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/metadata")
    public ResponseEntity<Void> metadata(@PathVariable String id, @RequestBody Map<String, String> body) {
        admin.replaceMetadata(id, body);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/steps/{stepName}/replay")
    public ResponseEntity<Void> replay(@PathVariable String id, @PathVariable String stepName) {
        admin.replayStep(id, stepName);
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/replay")
    public WorkflowAdminService.BatchReplayResult replaySuspended(@RequestBody WorkflowQuery query) {
        return admin.replaySuspendedSteps(query);
    }

    record WorkflowGraph(String workflow, List<StepGraph> steps, List<RouteGraph> routes) { }
    record StepGraph(String name, String type, boolean async) { }
    record RouteGraph(String from, String status, List<String> steps, String join) { }
}
