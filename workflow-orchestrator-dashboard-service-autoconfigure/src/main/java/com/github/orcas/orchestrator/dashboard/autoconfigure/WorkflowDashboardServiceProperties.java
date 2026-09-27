package com.github.orcas.orchestrator.dashboard.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka dashboard endpoint settings. */
@ConfigurationProperties("workflow.orchestrator.dashboard")
public class WorkflowDashboardServiceProperties {
    private boolean enabled = true;
    private String basePath = "/api/orchestrator/kafka";
    private long timeoutSeconds = 5;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getBasePath() { return basePath; }
    public void setBasePath(String value) { basePath = value; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long value) { timeoutSeconds = value; }
}
