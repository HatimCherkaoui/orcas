package com.github.orcas.orchestrator.resilience.autoconfigure;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowCircuitBreakerPropertiesTest {
    @Test
    void exposesUsefulDefaults() {
        var properties = new WorkflowCircuitBreakerProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getSlidingWindowSize()).isEqualTo(20);
        assertThat(properties.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void keepsPerInstanceOverridesSeparateFromDefaults() {
        var properties = new WorkflowCircuitBreakerProperties();
        var instance = new WorkflowCircuitBreakerProperties.Instance();
        instance.setSlidingWindowSize(5);
        properties.getInstances().put("reserve", instance);

        assertThat(properties.getInstances().get("reserve").getSlidingWindowSize()).isEqualTo(5);
        assertThat(properties.getSlidingWindowSize()).isEqualTo(20);
    }
}
