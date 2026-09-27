package com.github.orcas.orchestrator.rest.autoconfigure;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRestClientPropertiesTest {
    @Test void hasSafeDefaults() {
        var properties = new WorkflowRestClientProperties();
        assertThat(properties.getMaxConnections()).isEqualTo(100);
        assertThat(properties.getCache().isEnabled()).isFalse();
        assertThat(properties.getPropagatedMetadataKeys()).contains("correlationid");
    }
}
