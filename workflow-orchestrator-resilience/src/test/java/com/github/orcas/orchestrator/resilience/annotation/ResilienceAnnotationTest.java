package com.github.orcas.orchestrator.resilience.annotation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAnnotationTest {
    @Test
    void defaultsToDerivedBreakerNameAndSuspension() {
        var annotation = Marker.class.getAnnotation(WorkflowCircuitBreaker.class);

        assertThat(annotation.name()).isEmpty();
        assertThat(annotation.fallback()).isEqualTo(FallbackStrategy.SUSPEND);
        assertThat(FallbackStrategy.values()).containsExactly(
                FallbackStrategy.SUSPEND, FallbackStrategy.REPLAY);
    }

    @WorkflowCircuitBreaker
    static final class Marker {
    }
}
