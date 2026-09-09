package com.github.orcas.orchestrator.autoconfigure.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@ConfigurationProperties("workflow.orchestrator.rest-client")
public class WorkflowRestClientProperties {
    private boolean enabled = true;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(30);
    private int maxConnections = 100;
    private Duration pendingAcquireTimeout = Duration.ofSeconds(45);
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private List<String> propagatedMetadataKeys = new java.util.ArrayList<>(List.of("correlationid", "x-correlation-id", "traceparent", "baggage"));
    private Security security = new Security();
    private Cache cache = new Cache();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration v) { connectTimeout = v; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration v) { responseTimeout = v; }
    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int v) { maxConnections = v; }
    public Duration getPendingAcquireTimeout() { return pendingAcquireTimeout; }
    public void setPendingAcquireTimeout(Duration v) { pendingAcquireTimeout = v; }
    public Map<String, String> getDefaultHeaders() { return defaultHeaders; }
    public List<String> getPropagatedMetadataKeys() { return propagatedMetadataKeys; }
    public Security getSecurity() { return security; }
    public Cache getCache() { return cache; }
    public static class Security {
        private String bearerToken;
        private String basicUsername;
        private String basicPassword;
        public String getBearerToken() { return bearerToken; }
        public void setBearerToken(String v) { bearerToken = v; }
        public String getBasicUsername() { return basicUsername; }
        public void setBasicUsername(String v) { basicUsername = v; }
        public String getBasicPassword() { return basicPassword; }
        public void setBasicPassword(String v) { basicPassword = v; }
    }
    public static class Cache {
        private boolean enabled = false;
        private Duration ttl = Duration.ofSeconds(30);
        private int maxEntries = 1000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { enabled = v; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration v) { ttl = v; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int v) { maxEntries = v; }
    }
}
