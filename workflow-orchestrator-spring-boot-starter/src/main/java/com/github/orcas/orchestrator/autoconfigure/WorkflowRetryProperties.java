package com.github.orcas.orchestrator.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures the automatic replay/retry behavior applied when a step fails with a
 * replayable {@code WorkflowError}, bound from {@code workflow.orchestrator.retry.*}.
 * Per-step overrides can be declared under {@code workflow.orchestrator.retry.steps.<name>.*}.
 */
@ConfigurationProperties("workflow.orchestrator.retry")
public class WorkflowRetryProperties {
    /** Master switch for automatic retries. */
    private boolean enabled = true;
    /** Default maximum number of replay attempts before a workflow is suspended. */
    private int maxAttempts = 5;
    /** Default delay between replay attempts. */
    private Duration delay = Duration.ofSeconds(5);
    /** Per-step overrides of {@link #maxAttempts} and {@link #delay}, keyed by step name. */
    private Map<String, StepRetry> steps = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int v) {
        maxAttempts = v;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration v) {
        delay = v;
    }

    public Map<String, StepRetry> getSteps() {
        return steps;
    }

    /** @throws IllegalArgumentException if {@link #maxAttempts} or {@link #delay} are invalid */
    public void validate() {
        if (maxAttempts < 1) throw new IllegalArgumentException("max-attempts must be >= 1");
        if (delay == null || delay.isNegative()) throw new IllegalArgumentException("delay must be >= 0");
    }

    /** Per-step retry override; unset fields fall back to the workflow-wide defaults. */
    public static class StepRetry {
        private Integer maxAttempts;
        private Duration delay;

        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Integer v) {
            maxAttempts = v;
        }

        public Duration getDelay() {
            return delay;
        }

        public void setDelay(Duration v) {
            delay = v;
        }
    }
}
