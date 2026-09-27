package com.github.orcas.orchestrator.resilience.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Delayed replay policy for replayable workflow failures. */
@ConfigurationProperties("workflow.orchestrator.retry")
public class WorkflowRetryProperties {
    private boolean enabled = true;
    private int maxAttempts = 5;
    private Duration delay = Duration.ofSeconds(5);
    private Map<String, StepRetry> steps = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { maxAttempts = value; }
    public Duration getDelay() { return delay; }
    public void setDelay(Duration value) { delay = value; }
    public Map<String, StepRetry> getSteps() { return steps; }

    public StepRetry forStep(String name) { return steps.get(name); }

    public void validate() {
        if (maxAttempts < 1) throw new IllegalArgumentException("retry.max-attempts must be >= 1");
        if (delay == null || delay.isNegative()) throw new IllegalArgumentException("retry.delay must be >= 0");
    }

    /** Step-specific overrides for the global delayed replay policy. */
    public static class StepRetry {
        private Integer maxAttempts;
        private Duration delay;
        public Integer getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(Integer value) { maxAttempts = value; }
        public Duration getDelay() { return delay; }
        public void setDelay(Duration value) { delay = value; }
    }
}
