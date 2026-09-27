package com.github.orcas.orchestrator.dashboard.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDashboardServicePropertiesTest {
    @Test
    void isDisabledByDefault() {
        assertThat(new WorkflowDashboardServiceProperties().isEnabled()).isTrue();
    }
}
