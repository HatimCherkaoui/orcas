package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a delayed, automatic replay of a suspended workflow step, typically used
 * by {@code WorkflowCircuitBreakerAspect} when a circuit breaker opens and its
 * fallback is configured to {@code "REPLAY"}.
 */
public final class WorkflowRetryScheduler implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRetryScheduler.class);

    private final WorkflowEngine engine;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public WorkflowRetryScheduler(WorkflowEngine engine) {
        this.engine = engine;
    }

    /**
     * Schedules {@code stepName} of {@code workflowId} to be replayed after {@code delay}.
     *
     * @param workflowId id of the workflow instance to replay
     * @param stepName   name of the step to replay
     * @param delay      how long to wait before replaying
     */
    public void schedule(String workflowId, String stepName, Duration delay) {
        log.info("Scheduling replay of step '{}' for workflow instance {} in {}", stepName, workflowId, delay);
        executor.schedule(() -> {
            try {
                engine.replay(workflowId, stepName);
            } catch (RuntimeException e) {
                log.error("Scheduled replay of step '{}' for workflow instance {} failed", stepName, workflowId, e);
                throw e;
            }
        }, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
