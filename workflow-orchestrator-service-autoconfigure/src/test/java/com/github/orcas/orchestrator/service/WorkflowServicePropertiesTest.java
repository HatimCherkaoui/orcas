package com.github.orcas.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowServicePropertiesTest {
    @Test
    void isDisabledByDefault() {
        assertThat(new WorkflowServiceProperties().isEnabled()).isTrue();
    }
}
