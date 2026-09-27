package com.github.orcas.orchestrator.resilience.autoconfigure;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRetryPropertiesTest {
    @Test void rejectsInvalidAttemptCount() {
        var properties = new WorkflowRetryProperties();
        properties.setMaxAttempts(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
