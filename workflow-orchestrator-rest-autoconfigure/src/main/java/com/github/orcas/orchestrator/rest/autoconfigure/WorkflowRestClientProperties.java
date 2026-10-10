package com.github.orcas.orchestrator.rest.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuration for generated workflow REST clients. */
@ConfigurationProperties("workflow.orchestrator.rest-client")
public final class WorkflowRestClientProperties {
    private boolean enabled = true;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(30);
    private int maxConnections = 100;
    private Duration pendingAcquireTimeout = Duration.ofSeconds(45);
    private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private final List<String> propagatedMetadataKeys = new ArrayList<>(
            List.of("correlationid", "x-correlation-id", "x-request-id", "x-transaction-id", "x-trace-id", "traceparent", "tracestate"));
    private final Security security = new Security();
    private final Cache cache = new Cache();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public Duration getPendingAcquireTimeout() {
        return pendingAcquireTimeout;
    }

    public void setPendingAcquireTimeout(Duration pendingAcquireTimeout) {
        this.pendingAcquireTimeout = pendingAcquireTimeout;
    }

    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    public List<String> getPropagatedMetadataKeys() {
        return propagatedMetadataKeys;
    }

    public Security getSecurity() {
        return security;
    }

    public Cache getCache() {
        return cache;
    }

    /** Credentials used when a generated client needs basic or bearer authentication. */
    public static final class Security {
        private String bearerToken;
        private String basicUsername;
        private String basicPassword;

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public String getBasicUsername() {
            return basicUsername;
        }

        public void setBasicUsername(String basicUsername) {
            this.basicUsername = basicUsername;
        }

        public String getBasicPassword() {
            return basicPassword;
        }

        public void setBasicPassword(String basicPassword) {
            this.basicPassword = basicPassword;
        }
    }

    /** Optional per-client response cache settings. */
    public static final class Cache {
        private boolean enabled;
        private Duration ttl = Duration.ofSeconds(30);
        private int maxEntries = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }
}
