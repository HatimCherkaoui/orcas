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
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Operational API used by the dashboard and by operators. */
@RestController
@RequestMapping("${workflow.orchestrator.service.base-path:/api/orchestrator}/workflows")
public final class WorkflowServiceController {
    private static final long MAX_CONTEXT_BYTES = 1024L * 1024L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    public ResponseEntity<StepContext> stepContext(@PathVariable String id, @PathVariable String stepName) {
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

    @GetMapping("/{id}/replays")
    public List<WorkflowQueryService.ReplayView> replays(@PathVariable String id) { return query.replays(id); }

    @GetMapping("/definitions/{workflow}")
    public WorkflowGraph definition(@PathVariable String workflow) {
        return new WorkflowGraph(workflow, List.of(), List.of());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateWorkflow(@PathVariable String id, @RequestBody Map<String, String> body) {
        return noContent(() -> admin.updateWorkflowStatus(id, body.get("status")));
    }

    @PatchMapping("/{id}/steps/{stepName}")
    public ResponseEntity<Void> updateStep(@PathVariable String id, @PathVariable String stepName,
                                            @RequestBody Map<String, String> body) {
        return noContent(() -> admin.updateStepState(id, stepName, body.get("state")));
    }

    @PutMapping("/{id}/context")
    public ResponseEntity<Void> context(@PathVariable String id, @RequestBody Object body) {
        validateContextPayload(body);
        return noContent(() -> admin.replaceContext(id, body));
    }

    @PutMapping("/{id}/metadata")
    public ResponseEntity<Void> metadata(@PathVariable String id, @RequestBody Map<String, String> body) {
        return noContent(() -> admin.replaceMetadata(id, body));
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<Void> abandon(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return accepted(() -> admin.abandonWorkflow(id, body == null ? null : body.get("reason")));
    }

    @PostMapping("/{id}/steps/{stepName}/replay")
    public ResponseEntity<Void> replay(
            @PathVariable String id,
            @PathVariable String stepName,
            @RequestBody(required = false) Object body) {
        if (body != null) {
            validateContextPayload(body);
            return accepted(() -> {
                admin.replaceContext(id, body);
                admin.replayStep(id, stepName);
            });
        }
        return accepted(() -> admin.replayStep(id, stepName));
    }

    @PostMapping("/replay")
    public WorkflowAdminService.BatchReplayResult replaySuspended(@RequestBody WorkflowQuery query) {
        return admin.replaySuspendedSteps(query);
    }

    private void validateContextPayload(Object body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context payload must not be null");
        }
        try {
            long size = MAPPER.writeValueAsBytes(body).length;
            if (size > MAX_CONTEXT_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context payload exceeds 1MB maximum size");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context payload is not valid JSON", e);
        }
        validateContextTree(body);
    }

    private void validateContextTree(Object value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context payload must not contain null values");
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                validateContextTree(nested);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object nested : collection) {
                validateContextTree(nested);
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                validateContextTree(Array.get(value, i));
            }
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Context payload contains unsupported type: " + value.getClass().getName()
        );
    }

    private ResponseEntity<Void> noContent(Runnable action) {
        try {
            action.run();
            return ResponseEntity.noContent().build();
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<Void> accepted(Runnable action) {
        try {
            action.run();
            return ResponseEntity.accepted().build();
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record WorkflowGraph(String workflow, List<StepGraph> steps, List<RouteGraph> routes) { }
    public record StepGraph(String name, String type, boolean async) { }
    public record RouteGraph(String from, String status, List<String> steps, String join) { }
}
