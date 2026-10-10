package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

class WorkflowJdbcAutoConfigurationTest {
    @Test
    void exposesJdbcOnlyDefaults() {
        var properties = new WorkflowJdbcProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isSchemaInitialization()).isTrue();
        assertThat(properties.getSchemaLocation()).isEqualTo("classpath:orchestrator-schema.sql");
    }

}
