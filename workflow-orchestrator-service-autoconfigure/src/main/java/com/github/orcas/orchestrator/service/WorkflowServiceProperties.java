package com.github.orcas.orchestrator.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the read/admin REST API. */
@ConfigurationProperties("workflow.orchestrator.service")
public class WorkflowServiceProperties {
    private boolean enabled = true;
    private String basePath = "/api/orchestrator";
    private int defaultPageSize = 25;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getBasePath() { return basePath; }
    public void setBasePath(String value) { basePath = value; }
    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int value) { defaultPageSize = value; }
}
