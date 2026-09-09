package com.github.orcas.orchestrator.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the admin/query REST API, bound from
 * {@code workflow.orchestrator.service.*}.
 */
@ConfigurationProperties("workflow.orchestrator.service")
public class WorkflowServiceProperties {
    /** Master switch for the admin/query REST API auto-configuration. */
    private boolean enabled = true;
    /** Base path under which every endpoint of this starter is exposed. */
    private String basePath = "/api/orchestrator";
    /** Default page size for the workflow search endpoint when {@code size} is omitted. */
    private int defaultPageSize = 25;
    private Kafka kafka = new Kafka();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }
    public Kafka getKafka() { return kafka; }

    /** Kafka introspection sub-settings, exposed via {@code KafkaServiceController}. */
    public static class Kafka {
        /** Whether the Kafka introspection endpoints/service are enabled. */
        private boolean enabled = true;
        /** Timeout, in seconds, applied to Kafka admin-client calls. */
        private long timeoutSeconds = 5;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
