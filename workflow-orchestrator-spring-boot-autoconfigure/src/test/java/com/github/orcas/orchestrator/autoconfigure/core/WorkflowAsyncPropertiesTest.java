package com.github.orcas.orchestrator.autoconfigure.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowAsyncPropertiesTest {
    @Test void defaultsToVirtualThreads() {
        var properties = new WorkflowAsyncProperties();
        assertThat(properties.isVirtualThreads()).isTrue();
        assertThat(properties.getConcurrency()).isEqualTo(16);
    }
}
