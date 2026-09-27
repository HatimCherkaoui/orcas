package com.github.orcas.orchestrator.resilience.autoconfigure;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.engine.WorkflowRetryStateStore;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
/**
 * Schedules delayed replays for suspended workflow steps and drives half-open probe
 * batches when a circuit breaker is ready to test recovery.
 */
public final class WorkflowRetryScheduler implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRetryScheduler.class);
    public record ScheduledHalfOpenReplay(String breakerName, String stepName, Instant scheduledAt,
                                          Duration delay, int permittedCalls, String reason) {
    }

    private final WorkflowEngine engine;
    private final WorkflowRetryStateStore stateStore;
    private final CircuitBreakerRegistry registry;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<String, ScheduledFuture<?>> pendingHalfOpenBatches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledHalfOpenReplay> scheduledHalfOpenReplays = new ConcurrentHashMap<>();
    public WorkflowRetryScheduler(WorkflowEngine engine) {
        this(engine, null, null);
    }
    public WorkflowRetryScheduler(WorkflowEngine engine, WorkflowRetryStateStore stateStore) {
        this(engine, stateStore, null);
    }
    public WorkflowRetryScheduler(WorkflowEngine engine, WorkflowRetryStateStore stateStore, CircuitBreakerRegistry registry) {
        this.engine = engine;
        this.stateStore = stateStore;
        this.registry = registry;
    }
    /** Schedules {@code stepName} of {@code workflowId} to be replayed after {@code delay}. */
    public void schedule(String workflowId, String stepName, Duration delay) {
        log.info("Scheduling replay of step '{}' for workflow instance {} in {}", stepName, workflowId, delay);
        recordRetryAttempt(workflowId, stepName, "circuit breaker fallback replay scheduled");
        executor.schedule(() -> {
            try {
                engine.replay(workflowId, stepName);
            } catch (RuntimeException e) {
                log.error("Scheduled replay of step '{}' for workflow instance {} failed", stepName, workflowId, e);
                throw e;
            }
        }, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
    }
    /**
     * Schedules a half-open probe batch for the breaker/step pair.
     * The batch is deduplicated per breaker+step so multiple OPEN events don't stack.
     */
    public void scheduleHalfOpenReplay(String breakerName, String stepName, Duration delay, int permittedCalls) {
        String key = breakerName + ":" + stepName;
        Duration safeDelay = delay == null ? Duration.ZERO : delay;
        int batchSize = Math.max(1, permittedCalls);
        pendingHalfOpenBatches.computeIfAbsent(key, ignored -> {
            scheduledHalfOpenReplays.put(key, new ScheduledHalfOpenReplay(
                    breakerName,
                    stepName,
                    Instant.now().plusMillis(Math.max(0, safeDelay.toMillis())),
                    safeDelay,
                    batchSize,
                    "circuit breaker half-open replay"));
            return executor.schedule(() -> {
                pendingHalfOpenBatches.remove(key);
                scheduledHalfOpenReplays.remove(key);
                runHalfOpenReplay(breakerName, stepName, safeDelay, batchSize);
            }, Math.max(0, safeDelay.toMillis()), TimeUnit.MILLISECONDS);
        });
    }

    public Optional<ScheduledHalfOpenReplay> scheduledHalfOpenReplay(String breakerName, String stepName) {
        return Optional.ofNullable(scheduledHalfOpenReplays.get(breakerName + ":" + stepName));
    }
    private void runHalfOpenReplay(String breakerName, String stepName, Duration delay, int batchSize) {
        if (stateStore == null || registry == null) {
            log.warn("Cannot run half-open replay for breaker '{}' step '{}' because state store or registry is unavailable",
                    breakerName, stepName);
            return;
        }
        CircuitBreaker breaker = registry.circuitBreaker(breakerName);
        List<String> suspended = stateStore.suspendedWorkflowIds(stepName);
        if (suspended.isEmpty()) {
            log.debug("No suspended workflow instances found for step '{}'", stepName);
            return;
        }
        Set<String> selected = new HashSet<>(suspended.stream().limit(batchSize).toList());
        log.info("Running half-open replay for breaker '{}' step '{}': probing {} of {} suspended workflow instance(s)",
                breakerName, stepName, selected.size(), suspended.size());
        replayWorkflowIds(stepName, selected, breaker, "circuit breaker half-open probe");
        if (breaker.getState() == CircuitBreaker.State.CLOSED) {
            replayRemaining(breakerName, stepName, selected, breaker, delay, batchSize);
        } else {
            log.info("Breaker '{}' is still {} after half-open probes; rescheduling step '{}' in {}",
                    breakerName, breaker.getState(), stepName, delay);
            scheduleHalfOpenReplay(breakerName, stepName, delay, batchSize);
        }
    }
    private void replayRemaining(String breakerName, String stepName, Set<String> alreadyReplayed,
                                 CircuitBreaker breaker, Duration delay, int batchSize) {
        List<String> remaining = stateStore.suspendedWorkflowIds(stepName).stream()
                .filter(workflowId -> !alreadyReplayed.contains(workflowId))
                .toList();
        if (remaining.isEmpty()) {
            return;
        }
        log.info("Breaker '{}' closed; replaying {} remaining suspended workflow instance(s) for step '{}'",
                breakerName, remaining.size(), stepName);
        replayWorkflowIds(stepName, new HashSet<>(remaining), breaker, "circuit breaker closed replay");
        if (breaker.getState() != CircuitBreaker.State.CLOSED) {
            log.info("Breaker '{}' reopened while replaying remaining suspended workflow instances for step '{}'; rescheduling in {}",
                    breakerName, stepName, delay);
            scheduleHalfOpenReplay(breakerName, stepName, delay, batchSize);
        }
    }
    private void replayWorkflowIds(String stepName, Set<String> workflowIds, CircuitBreaker breaker, String reason) {
        for (String workflowId : workflowIds) {
            recordRetryAttempt(workflowId, stepName, reason);
            try {
                engine.replay(workflowId, stepName);
            } catch (RuntimeException e) {
                log.warn("Replay of step '{}' for workflow instance {} failed: {}", stepName, workflowId, e.getMessage());
            }
            if (breaker.getState() == CircuitBreaker.State.OPEN) {
                return;
            }
        }
    }
    private void recordRetryAttempt(String workflowId, String stepName, String reason) {
        if (stateStore == null) {
            return;
        }
        try {
            stateStore.recordRetry(workflowId, stepName, 0, reason);
        } catch (RuntimeException e) {
            log.warn("Unable to record retry for step '{}' of workflow instance {}: {}", stepName, workflowId, e.getMessage());
        }
    }
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
