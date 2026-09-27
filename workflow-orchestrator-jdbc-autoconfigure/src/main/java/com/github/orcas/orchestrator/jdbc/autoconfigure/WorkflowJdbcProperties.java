package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** PostgreSQL/JDBC persistence settings. */
@ConfigurationProperties("workflow.orchestrator.jdbc")
public class WorkflowJdbcProperties {
    private boolean enabled = true;
    private boolean schemaInitialization = true;
    private String schemaLocation = "classpath:orchestrator-schema.sql";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isSchemaInitialization() { return schemaInitialization; }
    public void setSchemaInitialization(boolean value) { schemaInitialization = value; }
    public String getSchemaLocation() { return schemaLocation; }
    public void setSchemaLocation(String value) { schemaLocation = value; }
}
