package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.autoconfigure.WorkflowCircuitBreakerProperties;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService.*;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryProperties;
import com.github.orcas.orchestrator.autoconfigure.WorkflowRetryScheduler;
import com.github.orcas.orchestrator.autoconfigure.rest.AsyncRestClientWorkflowStep;
import com.github.orcas.orchestrator.autoconfigure.rest.RestClientWorkflowStep;
import com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.MethodAsyncWorkflowStep;
import com.github.orcas.orchestrator.core.api.MethodWorkflowStep;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.engine.WorkflowRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API backing the operator dashboard: read-only search/inspection endpoints
 * (workflow search, step/context/metadata detail, audit logs, routing graph) plus
 * administrative mutation endpoints (status/state overrides, replay, context and
 * metadata edits). Every mutation is logged at {@code INFO} with the workflow id and
 * (where applicable) step name, so operator actions are traceable in the same log
 * stream as engine-driven state transitions.
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

    public WorkflowServiceController(WorkflowQueryService query, WorkflowAdminService admin,
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
    public PageResult<WorkflowSummary> search(
            @RequestParam(name = "pipelineId", required = false) String pipelineId,
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
        return query.search(new WorkflowQuery(pipelineId, workflow, status, stepName, stepStatus,
                metadataKey, metadataValue, createdFrom, createdTo, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDetails> find(@PathVariable("id") String id) {
        return ResponseEntity.of(query.find(id));
    }

    @GetMapping("/definitions/{workflow}")
    public ResponseEntity<WorkflowGraph> definition(@PathVariable("workflow") String workflow) {
        var definition = registry.get(workflow);
        if (definition == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(WorkflowGraph.from(definition, retryProperties, circuitBreakerProperties));
    }

    @GetMapping("/{id}/steps")
    public java.util.List<WorkflowStepView> steps(@PathVariable("id") String id) {
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
        String circuitBreakerState = stepConfig == null ? null : stepConfig.circuitBreakerName() == null
                ? null
                : resolveCircuitBreakerState(stepConfig.circuitBreakerName());
        ScheduledRetryView scheduledRetry = stepConfig == null || stepConfig.circuitBreakerName() == null
                ? null
                : resolveScheduledRetry(stepConfig.circuitBreakerName(), stepName);
        return ResponseEntity.ok(new WorkflowStepDetailsView(persisted, stepConfig, circuitBreakerState, scheduledRetry));
    }

    @GetMapping("/{id}/steps/{stepName}/context")
    public ResponseEntity<com.github.orcas.orchestrator.core.model.StepContext> stepContext(
            @PathVariable("id") String id, @PathVariable("stepName") String stepName) {
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
    public java.util.List<EntityLogView> logs(@PathVariable("id") String id) {
        return query.workflowLogs(id);
    }

    @GetMapping("/{id}/steps/{stepName}/logs")
    public java.util.List<EntityLogView> stepLogs(@PathVariable("id") String id,
                                                  @PathVariable("stepName") String stepName) {
        return query.stepLogs(id, stepName);
    }

    @GetMapping("/{id}/context/logs")
    public java.util.List<EntityLogView> contextLogs(@PathVariable("id") String id) {
        return query.contextLogs(id);
    }

    @GetMapping("/{id}/metadata/logs")
    public java.util.List<EntityLogView> metadataLogs(@PathVariable("id") String id) {
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
    public ResponseEntity<Void> replayStep(@PathVariable("id") String id, @PathVariable("stepName") String stepName) {
        log.info("Operator requested replay of step '{}' for workflow instance {}", stepName, id);
        admin.replayStep(id, stepName);
        return ResponseEntity.accepted().build();
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
        return scheduler == null ? null : scheduler.scheduledHalfOpenReplay(breakerName, stepName)
                .map(ScheduledRetryView::from)
                .orElse(null);
    }
}

/**
 * Routing graph of a workflow definition, plus the static per-step configuration
 * ({@code stepConfigs}) resolved from the definition and the effective retry
 * properties. The dashboard renders {@code routes} as the graph edges and surfaces
 * {@code stepConfigs} in the step details "Configuration" tab.
 */
record WorkflowGraph(String workflow, java.util.List<RouteGraph> routes, Map<String, StepConfig> stepConfigs) {
    static WorkflowGraph from(WorkflowDefinition d, WorkflowRetryProperties retry, WorkflowCircuitBreakerProperties circuitBreakerProperties) {
        Map<String, StepConfig> configs = new LinkedHashMap<>();
        for (WorkflowDefinition.Route route : d.routes()) {
            route.steps().forEach(step -> configs.putIfAbsent(step.name(), StepConfig.from(step, retry, circuitBreakerProperties)));
            if (route.joinStep() != null) {
                configs.putIfAbsent(route.joinStep().name(), StepConfig.from(route.joinStep(), retry, circuitBreakerProperties));
            }
        }
        return new WorkflowGraph(d.name(), d.routes().stream().map(RouteGraph::from).toList(), configs);
    }
}

/**
 * Static configuration of a single step as the engine will actually apply it:
 * whether it runs asynchronously, and the effective retry policy (per-step override
 * from {@code workflow.orchestrator.retry.steps.<name>.*} when present, otherwise the
 * workflow-wide defaults).
 *
 * @param stepName    the step's unique name
 * @param async       {@code true} when the step is an {@link AsyncStep} (dispatched to the async executor)
 * @param retryEnabled whether automatic retries are enabled at all
 * @param maxAttempts effective maximum number of attempts before the workflow suspends
 * @param delayMillis effective delay between attempts, in milliseconds
 * @param overridden  {@code true} when a per-step retry override is configured for this step
 * @param circuitBreakerEnabled {@code true} when the step is annotated/configured with a circuit breaker
 * @param circuitBreakerName effective circuit breaker name, when configured
 * @param circuitBreakerFallback effective fallback strategy for OPEN breaker state
 * @param circuitBreakerWaitOpenMillis effective OPEN wait duration, in milliseconds
 * @param circuitBreakerPermittedHalfOpenCalls effective half-open probe batch size
 */
record StepConfig(String stepName, boolean async, boolean retryEnabled,
                  int maxAttempts, long delayMillis, boolean overridden,
                  boolean circuitBreakerEnabled, String circuitBreakerName, String circuitBreakerFallback,
                  Long circuitBreakerWaitOpenMillis, Integer circuitBreakerPermittedHalfOpenCalls) {
    static StepConfig from(com.github.orcas.orchestrator.core.api.WorkflowStep step,
                           WorkflowRetryProperties retry,
                           WorkflowCircuitBreakerProperties circuitBreakerProperties) {
        var override = retry.getSteps().get(step.name());
        int attempts = override != null && override.getMaxAttempts() != null
                ? override.getMaxAttempts() : retry.getMaxAttempts();
        var delay = override != null && override.getDelay() != null
                ? override.getDelay() : retry.getDelay();
        CircuitBreakerStepMetadata circuitBreaker = StepMetadataSupport.circuitBreaker(step, circuitBreakerProperties);
        return new StepConfig(step.name(), step instanceof AsyncStep, retry.isEnabled(),
                attempts, delay == null ? 0L : delay.toMillis(), override != null,
                circuitBreaker != null, circuitBreaker == null ? null : circuitBreaker.name(),
                circuitBreaker == null ? null : circuitBreaker.fallback(),
                circuitBreaker == null ? null : circuitBreaker.waitOpenMillis(),
                circuitBreaker == null ? null : circuitBreaker.permittedHalfOpenCalls());
    }
}

record WorkflowStepDetailsView(WorkflowStepView step, StepConfig stepConfig,
                               String circuitBreakerState, ScheduledRetryView scheduledRetry) {
}

record ScheduledRetryView(String type, String reason, String breakerName, String stepName,
                          Instant scheduledAt, long remainingMillis, int batchSize) {
    static ScheduledRetryView from(WorkflowRetryScheduler.ScheduledHalfOpenReplay scheduled) {
        long remainingMillis = Math.max(0, scheduled.scheduledAt().toEpochMilli() - Instant.now().toEpochMilli());
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

record RouteGraph(String triggerStep, String triggerStatus, String mode, java.util.List<String> branches, String joinStep) {
    static RouteGraph from(WorkflowDefinition.Route r) {
        String trigger = r.criteria().expectedStep();
        String status = r.criteria().expectedStatus().name();
        String mode = r.joinStep() != null ? "PARALLEL" : r.steps().stream().anyMatch(s -> s instanceof AsyncStep) ? "ASYNC" : "SEQUENTIAL";
        return new RouteGraph(trigger, status, mode, r.steps().stream().map(com.github.orcas.orchestrator.core.api.WorkflowStep::name).toList(), r.joinStep() == null ? null : r.joinStep().name());
    }
}

record CircuitBreakerStepMetadata(String name, String fallback, Long waitOpenMillis, Integer permittedHalfOpenCalls) {
}

final class StepMetadataSupport {
    private StepMetadataSupport() {
    }

    static CircuitBreakerStepMetadata circuitBreaker(WorkflowStep step,
                                                     WorkflowCircuitBreakerProperties properties) {
        WorkflowCircuitBreaker annotation = annotation(step);
        if (annotation == null) {
            return null;
        }
        WorkflowCircuitBreakerProperties.Instance instance = properties.getInstances().get(annotation.name());
        var wait = instance != null && instance.getWaitDurationInOpenState() != null
                ? instance.getWaitDurationInOpenState()
                : properties.getWaitDurationInOpenState();
        Integer permittedHalfOpenCalls = instance != null && instance.getPermittedNumberOfCallsInHalfOpenState() != null
                ? instance.getPermittedNumberOfCallsInHalfOpenState()
                : properties.getPermittedNumberOfCallsInHalfOpenState();
        return new CircuitBreakerStepMetadata(
                annotation.name(),
                annotation.fallback().name(),
                wait == null ? null : wait.toMillis(),
                permittedHalfOpenCalls);
    }

    private static WorkflowCircuitBreaker annotation(WorkflowStep step) {
        if (step == null) {
            return null;
        }
        if (step instanceof AsyncRestClientWorkflowStep asyncRestStep) {
            Object delegate = readField(asyncRestStep, "delegate");
            return delegate instanceof WorkflowStep workflowStep ? annotation(workflowStep) : null;
        }
        if (step instanceof RestClientWorkflowStep restClientWorkflowStep) {
            return (WorkflowCircuitBreaker) readField(restClientWorkflowStep, "circuitBreaker");
        }
        if (step instanceof MethodWorkflowStep methodWorkflowStep) {
            return methodAnnotation(methodWorkflowStep);
        }
        if (step instanceof MethodAsyncWorkflowStep methodAsyncWorkflowStep) {
            return methodAnnotation(methodAsyncWorkflowStep);
        }
        return AnnotatedElementUtils.findMergedAnnotation(step.getClass(), WorkflowCircuitBreaker.class);
    }

    private static WorkflowCircuitBreaker methodAnnotation(Object methodStep) {
        Method method = (Method) readField(methodStep, "method");
        Object target = readField(methodStep, "target");
        WorkflowCircuitBreaker annotation = method == null ? null
                : AnnotatedElementUtils.findMergedAnnotation(method, WorkflowCircuitBreaker.class);
        return annotation != null || target == null
                ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(target.getClass(), WorkflowCircuitBreaker.class);
    }

    private static Object readField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.trySetAccessible();
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to inspect workflow step metadata", e);
            }
        }
        return null;
    }
}

