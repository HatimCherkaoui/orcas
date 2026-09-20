package com.github.orcas.orchestrator.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures the Resilience4j circuit breakers wrapping
 * {@code @WorkflowCircuitBreaker}-annotated steps, bound from
 * {@code workflow.orchestrator.circuit-breaker.*}. Per-breaker overrides can be
 * declared under {@code workflow.orchestrator.circuit-breaker.instances.<name>.*}.
 */
@ConfigurationProperties("workflow.orchestrator.circuit-breaker")
public class WorkflowCircuitBreakerProperties {
    /** Master switch for circuit breaker auto-configuration. */
    private boolean enabled = true;
    /** Default size of the sliding window used to compute the failure rate. */
    private int slidingWindowSize = 20;
    /** Default minimum number of calls before the failure rate is evaluated. */
    private int minimumNumberOfCalls = 10;
    /** Default failure rate threshold (percentage) that trips the breaker to OPEN. */
    private float failureRateThreshold = 50;
    /** Default number of half-open probe calls allowed before the breaker decides whether to close. */
    private int permittedNumberOfCallsInHalfOpenState = 10;
    /** Default duration the breaker stays OPEN before moving to HALF_OPEN. */
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);
    /** Per-breaker overrides, keyed by the {@code name} used in {@code @WorkflowCircuitBreaker}. */
    private Map<String, Instance> instances = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public void setSlidingWindowSize(int v) {
        slidingWindowSize = v;
    }

    public int getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public void setMinimumNumberOfCalls(int v) {
        minimumNumberOfCalls = v;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(float v) {
        failureRateThreshold = v;
    }

    public int getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    public void setPermittedNumberOfCallsInHalfOpenState(int v) {
        permittedNumberOfCallsInHalfOpenState = v;
    }

    public Duration getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    public void setWaitDurationInOpenState(Duration v) {
        waitDurationInOpenState = v;
    }

    public Map<String, Instance> getInstances() {
        return instances;
    }

    /** Per-breaker override of the workflow-wide circuit breaker defaults. */
    public static class Instance {
        private Integer slidingWindowSize;
        private Integer minimumNumberOfCalls;
        private Float failureRateThreshold;
        private Integer permittedNumberOfCallsInHalfOpenState;
        private Duration waitDurationInOpenState;

        public Integer getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(Integer v) {
            slidingWindowSize = v;
        }

        public Integer getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(Integer v) {
            minimumNumberOfCalls = v;
        }

        public Float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(Float v) {
            failureRateThreshold = v;
        }

        public Integer getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(Integer v) {
            permittedNumberOfCallsInHalfOpenState = v;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration v) {
            waitDurationInOpenState = v;
        }
    }
}
