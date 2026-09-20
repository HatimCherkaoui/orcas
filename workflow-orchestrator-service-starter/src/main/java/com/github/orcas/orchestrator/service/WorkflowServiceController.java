package com.github.orcas.orchestrator.service;
import com.github.orcas.orchestrator.autoconfigure.WorkflowCircuitBreakerProperties;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryProperties;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryScheduler;
import com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.MethodAsyncWorkflowStep;
import com.github.orcas.orchestrator.core.api.MethodWorkflowStep;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.engine.WorkflowRegistry;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.ContextView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.EntityLogView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.MetadataView;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.PageResult;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowDetails;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowQuery;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.WorkflowStepView;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Operational REST API exposing workflow search/detail/replay actions for the dashboard.
 */
@RestController
@RequestMapping("${workflow.orchestrator.service.base-path:/api/orchestrator}/workflows")
public final class WorkflowServiceController {
    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceController.class);
    private final WorkflowQueryService query;
    private final WorkflowAdminService admin;
    private final WorkflowRegistry registry;
    private final WorkflowRetryProperties retryProperties;
    private final WorkflowCircuitBreakerProperties circuitBreakerProperties;
    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider;
    private final ObjectProvider<WorkflowRetryScheduler> retrySchedulerProvider;
    public WorkflowServiceController(WorkflowQueryService query,
                                     WorkflowAdminService admin,
                                     WorkflowRegistry registry,
                                     WorkflowRetryProperties retryProperties,
                                     WorkflowCircuitBreakerProperties circuitBreakerProperties,
                                     ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
                                     ObjectProvider<WorkflowRetryScheduler> retrySchedulerProvider) {
        this.query = query;
        this.admin = admin;
        this.registry = registry;
        this.retryProperties = retryProperties;
        this.circuitBreakerProperties = circuitBreakerProperties;
        this.circuitBreakerRegistryProvider = circuitBreakerRegistryProvider;
        this.retrySchedulerProvider = retrySchedulerProvider;
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
        return query.search(workflowQuery(workflowId, workflow, status, stepName, stepStatus,
                metadataKey, metadataValue, createdFrom, createdTo, page, size));
    }
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDetails> find(@PathVariable("id") String id) {
        return ResponseEntity.of(query.find(id));
    }
    @GetMapping("/definitions/{workflow}")
    public ResponseEntity<WorkflowGraph> definition(@PathVariable("workflow") String workflow) {
        WorkflowDefinition definition = registry.get(workflow);
        if (definition == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(WorkflowGraph.from(definition, retryProperties, circuitBreakerProperties));
    }
    @GetMapping("/{id}/steps")
    public List<WorkflowStepView> steps(@PathVariable("id") String id) {
        return query.steps(id);
    }
    @GetMapping("/{id}/steps/{stepName}")
    public ResponseEntity<WorkflowStepDetailsView> step(@PathVariable("id") String id,
                                                        @PathVariable("stepName") String stepName) {
        final WorkflowStepView persisted;
        try {
            persisted = query.step(id, stepName);
        } catch (EmptyResultDataAccessException ex) {
            return ResponseEntity.notFound().build();
        }
        WorkflowDefinition definition = registry.get(persisted.workflow());
        WorkflowStep workflowStep = definition == null ? null : definition.findStep(stepName);
        StepConfig stepConfig = workflowStep == null ? null : StepConfig.from(workflowStep, retryProperties, circuitBreakerProperties);
        String circuitBreakerState = stepConfig == null || stepConfig.circuitBreakerName() == null
                ? null
                : resolveCircuitBreakerState(stepConfig.circuitBreakerName());
        ScheduledRetryView scheduledRetry = stepConfig == null || stepConfig.circuitBreakerName() == null
                ? null
                : resolveScheduledRetry(stepConfig.circuitBreakerName(), stepName);
        return ResponseEntity.ok(new WorkflowStepDetailsView(persisted, stepConfig, circuitBreakerState, scheduledRetry));
    }
    @GetMapping("/{id}/steps/{stepName}/context")
    public ResponseEntity<com.github.orcas.orchestrator.core.model.StepContext> stepContext(
            @PathVariable("id") String id,
            @PathVariable("stepName") String stepName) {
        return ResponseEntity.of(query.stepContext(id, stepName));
    }
    @GetMapping("/{id}/context")
    public ResponseEntity<ContextView> context(@PathVariable("id") String id) {
        return ResponseEntity.of(query.context(id));
    }
    @GetMapping("/{id}/metadata")
    public ResponseEntity<MetadataView> metadata(@PathVariable("id") String id) {
        return ResponseEntity.of(query.metadata(id));
    }
    @GetMapping("/{id}/logs")
    public List<EntityLogView> logs(@PathVariable("id") String id) {
        return query.workflowLogs(id);
    }
    @GetMapping("/{id}/steps/{stepName}/logs")
    public List<EntityLogView> stepLogs(@PathVariable("id") String id,
                                        @PathVariable("stepName") String stepName) {
        return query.stepLogs(id, stepName);
    }
    @GetMapping("/{id}/context/logs")
    public List<EntityLogView> contextLogs(@PathVariable("id") String id) {
        return query.contextLogs(id);
    }
    @GetMapping("/{id}/metadata/logs")
    public List<EntityLogView> metadataLogs(@PathVariable("id") String id) {
        return query.metadataLogs(id);
    }
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateWorkflow(@PathVariable("id") String id,
                                               @RequestBody Map<String, String> body) {
        log.info("Operator override: workflow instance {} status -> {}", id, body.get("status"));
        admin.updateWorkflowStatus(id, body.get("status"));
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/steps/{stepName}")
    public ResponseEntity<Void> updateStep(@PathVariable("id") String id,
                                           @PathVariable("stepName") String stepName,
                                           @RequestBody Map<String, String> body) {
        log.info("Operator override: workflow instance {} step '{}' state -> {}", id, stepName, body.get("state"));
        admin.updateStepState(id, stepName, body.get("state"));
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/context")
    public ResponseEntity<Void> updateContext(@PathVariable("id") String id,
                                              @RequestBody Object body) {
        log.info("Operator override: replacing context of workflow instance {}", id);
        admin.replaceContext(id, body);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/steps/{stepName}/replay")
    public ResponseEntity<Void> replayStep(@PathVariable("id") String id,
                                           @PathVariable("stepName") String stepName) {
        log.info("Operator requested replay of step '{}' for workflow instance {}", stepName, id);
        admin.replayStep(id, stepName);
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/replays/suspended")
    public ResponseEntity<WorkflowAdminService.BatchReplayResult> replaySuspendedSteps(
            @RequestParam(name = "workflowId", required = false) String workflowId,
            @RequestParam(name = "workflow", required = false) String workflow,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "stepName", required = false) String stepName,
            @RequestParam(name = "stepStatus", required = false) String stepStatus,
            @RequestParam(name = "metadataKey", required = false) String metadataKey,
            @RequestParam(name = "metadataValue", required = false) String metadataValue,
            @RequestParam(name = "createdFrom", required = false) Instant createdFrom,
            @RequestParam(name = "createdTo", required = false) Instant createdTo) {
        WorkflowQuery filters = workflowQuery(workflowId, workflow, status, stepName, stepStatus,
                metadataKey, metadataValue, createdFrom, createdTo, 0, 200);
        log.info("Operator requested batch replay of suspended workflow steps with filters workflowId='{}', workflow='{}', status='{}', stepName='{}', stepStatus='{}'",
                workflowId, workflow, status, stepName, stepStatus);
        return ResponseEntity.accepted().body(admin.replaySuspendedSteps(filters));
    }
    @PutMapping("/{id}/metadata")
    public ResponseEntity<Void> updateMetadata(@PathVariable("id") String id,
                                               @RequestBody Map<String, String> body) {
        log.info("Operator override: replacing metadata of workflow instance {}", id);
        admin.replaceMetadata(id, body);
        return ResponseEntity.noContent().build();
    }
    private String resolveCircuitBreakerState(String breakerName) {
        CircuitBreakerRegistry circuitBreakerRegistry = circuitBreakerRegistryProvider.getIfAvailable();
        return circuitBreakerRegistry == null ? null : circuitBreakerRegistry.circuitBreaker(breakerName).getState().name();
    }
    private ScheduledRetryView resolveScheduledRetry(String breakerName, String stepName) {
        WorkflowRetryScheduler scheduler = retrySchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return null;
        }
        return scheduler.scheduledHalfOpenReplay(breakerName, stepName)
                .map(ScheduledRetryView::from)
                .orElse(null);
    }
    private WorkflowQuery workflowQuery(String workflowId, String workflow, String status,
                                        String stepName, String stepStatus,
                                        String metadataKey, String metadataValue,
                                        Instant createdFrom, Instant createdTo,
                                        int page, int size) {
        return new WorkflowQuery(workflowId, workflow, status, stepName, stepStatus,
                metadataKey, metadataValue, createdFrom, createdTo, page, size);
    }
}
record WorkflowGraph(String workflow, List<RouteGraph> routes, Map<String, StepConfig> stepConfigs) {
    static WorkflowGraph from(WorkflowDefinition definition,
                              WorkflowRetryProperties retry,
                              WorkflowCircuitBreakerProperties circuitBreakerProperties) {
        List<RouteGraph> routes = definition.routes().stream().map(RouteGraph::from).toList();
        Map<String, StepConfig> configs = new LinkedHashMap<>();
        for (WorkflowDefinition.Route route : definition.routes()) {
            route.steps().forEach(step -> configs.putIfAbsent(step.name(), StepConfig.from(step, retry, circuitBreakerProperties)));
            if (route.joinStep() != null) {
                configs.putIfAbsent(route.joinStep().name(), StepConfig.from(route.joinStep(), retry, circuitBreakerProperties));
            }
        }
        return new WorkflowGraph(definition.name(), routes, configs);
    }
}
record StepConfig(String stepName,
                  boolean async,
                  boolean retryEnabled,
                  int maxAttempts,
                  long delayMillis,
                  boolean overridden,
                  boolean circuitBreakerEnabled,
                  String circuitBreakerName,
                  String circuitBreakerFallback,
                  Long circuitBreakerWaitOpenMillis,
                  Integer circuitBreakerPermittedHalfOpenCalls) {
    static StepConfig from(WorkflowStep step,
                           WorkflowRetryProperties retry,
                           WorkflowCircuitBreakerProperties circuitBreakerProperties) {
        WorkflowRetryProperties.StepRetry stepRetry = retry.getSteps().get(step.name());
        boolean retryEnabled = retry.isEnabled();
        int maxAttempts = stepRetry != null && stepRetry.getMaxAttempts() != null ? stepRetry.getMaxAttempts() : retry.getMaxAttempts();
        Duration delay = stepRetry != null && stepRetry.getDelay() != null ? stepRetry.getDelay() : retry.getDelay();
        WorkflowCircuitBreaker annotation = circuitBreaker(step);
        WorkflowCircuitBreakerProperties.Instance breakerInstance = annotation == null ? null : circuitBreakerProperties.getInstances().get(annotation.name());
        Duration waitOpen = breakerInstance != null && breakerInstance.getWaitDurationInOpenState() != null
                ? breakerInstance.getWaitDurationInOpenState()
                : circuitBreakerProperties.getWaitDurationInOpenState();
        Integer halfOpenCalls = breakerInstance != null && breakerInstance.getPermittedNumberOfCallsInHalfOpenState() != null
                ? breakerInstance.getPermittedNumberOfCallsInHalfOpenState()
                : circuitBreakerProperties.getPermittedNumberOfCallsInHalfOpenState();
        return new StepConfig(
                step.name(),
                step instanceof AsyncStep,
                retryEnabled,
                maxAttempts,
                delay == null ? 0L : delay.toMillis(),
                stepRetry != null,
                annotation != null && circuitBreakerProperties.isEnabled(),
                annotation == null ? null : annotation.name(),
                annotation == null ? null : annotation.fallback().name(),
                annotation == null || waitOpen == null ? null : waitOpen.toMillis(),
                annotation == null ? null : halfOpenCalls);
    }
    private static WorkflowCircuitBreaker circuitBreaker(WorkflowStep step) {
        if (step == null) {
            return null;
        }
        if (step instanceof MethodWorkflowStep || step instanceof MethodAsyncWorkflowStep) {
            Method method = field(step, "method", Method.class);
            WorkflowCircuitBreaker annotation = AnnotatedElementUtils.findMergedAnnotation(method, WorkflowCircuitBreaker.class);
            return annotation != null ? annotation : AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), WorkflowCircuitBreaker.class);
        }
        Object delegate = step;
        if (step.getClass().getName().endsWith("AsyncRestClientWorkflowStep")) {
            delegate = field(step, "delegate", Object.class);
        }
        if (delegate != null && delegate.getClass().getName().endsWith("RestClientWorkflowStep")) {
            return field(delegate, "circuitBreaker", WorkflowCircuitBreaker.class);
        }
        return AnnotatedElementUtils.findMergedAnnotation(step.getClass(), WorkflowCircuitBreaker.class);
    }
    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.trySetAccessible();
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to inspect workflow step " + target.getClass().getName() + '#' + name, e);
        }
    }
}
record WorkflowStepDetailsView(WorkflowStepView step,
                               StepConfig stepConfig,
                               String circuitBreakerState,
                               ScheduledRetryView scheduledRetry) {
}
record ScheduledRetryView(String type,
                          String reason,
                          String breakerName,
                          String stepName,
                          Instant scheduledAt,
                          long remainingMillis,
                          Integer batchSize) {
    static ScheduledRetryView from(WorkflowRetryScheduler.ScheduledHalfOpenReplay scheduled) {
        long remainingMillis = Math.max(0L, Duration.between(Instant.now(), scheduled.scheduledAt()).toMillis());
        return new ScheduledRetryView(
                "HALF_OPEN_REPLAY",
                scheduled.reason(),
                scheduled.breakerName(),
                scheduled.stepName(),
                scheduled.scheduledAt(),
                remainingMillis,
                scheduled.permittedCalls());
    }
}
record RouteGraph(String triggerStep,
                  String triggerStatus,
                  String mode,
                  List<String> branches,
                  String joinStep) {
    static RouteGraph from(WorkflowDefinition.Route route) {
        String mode;
        if (route.joinStep() != null) {
            mode = "PARALLEL";
        } else if (route.steps().stream().anyMatch(AsyncStep.class::isInstance)) {
            mode = "ASYNC";
        } else {
            mode = "SEQUENTIAL";
        }
        return new RouteGraph(
                route.criteria().expectedStep(),
                route.criteria().expectedStatus().name(),
                mode,
                route.steps().stream().map(WorkflowStep::name).toList(),
                route.joinStep() == null ? null : route.joinStep().name());
    }
}
