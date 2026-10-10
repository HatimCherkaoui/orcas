package com.github.orcas.orchestrator.observability.autoconfigure;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowObservabilityPropertiesTest {
    @Test void enablesMdcByDefault() {
        assertThat(new WorkflowObservabilityProperties().isMdc()).isTrue();
    }
}
