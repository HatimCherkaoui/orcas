package com.github.orcas.orchestrator.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration properties for the workflow orchestrator, bound from the
 * {@code workflow.orchestrator.*} namespace (see {@code application.yml}).
 *
 * <p>Nested groups: {@link Persistence} ({@code workflow.orchestrator.persistence.*}),
 * {@link Async} ({@code workflow.orchestrator.async.*}) and {@link Kafka}
 * ({@code workflow.orchestrator.kafka.*}). Related properties live in the sibling
 * {@link WorkflowRetryProperties} and {@link WorkflowCircuitBreakerProperties} classes.
 */
@ConfigurationProperties("workflow.orchestrator")
public class WorkflowProperties {
    /** Kafka topic used to publish/consume {@code StatusEvent}s that drive the engine. */
    private String topic = "workflow.status";
    /** Consumer group id used when consuming status events from Kafka. */
    private String consumerGroup = "workflow-orchestrator";
    /** Whether orchestrator auto-configuration is active. */
    private boolean enabled = true;
    private Persistence persistence = new Persistence();
    private Async async = new Async();
    private Kafka kafka = new Kafka();

    public String getTopic() {
        return topic;
    }

    public void setTopic(String v) {
        topic = v;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String v) {
        consumerGroup = v;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Async getAsync() {
        return async;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public static class Persistence {
        /** Whether the bundled {@code orchestrator-schema.sql} should be executed on startup. */
        private boolean schemaInitialization = true;
        /** Classpath/filesystem location of the schema script to execute. */
        private String schemaLocation = "classpath:orchestrator-schema.sql";

        public boolean isSchemaInitialization() {
            return schemaInitialization;
        }

        public void setSchemaInitialization(boolean v) {
            schemaInitialization = v;
        }

        public String getSchemaLocation() {
            return schemaLocation;
        }

        public void setSchemaLocation(String v) {
            schemaLocation = v;
        }
    }

    public static class Async {
        /** Use a virtual-thread-per-task executor instead of a fixed thread pool. */
        private boolean virtualThreads = true;
        /** Fixed thread pool size, only used when {@link #virtualThreads} is {@code false}. */
        private int concurrency = 16;

        public boolean isVirtualThreads() {
            return virtualThreads;
        }

        public void setVirtualThreads(boolean v) {
            virtualThreads = v;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int v) {
            concurrency = v;
        }
    }

    public static class Kafka {
        /** Number of concurrent consumer threads for the status-event listener container. */
        private int concurrency = 1;
        /** Whether the application fails to start if the configured topics do not exist. */
        private boolean missingTopicsFatal = false;
        /**
         * Enables Micrometer {@code Observation} instrumentation on the Kafka
         * template/listener container, which propagates distributed tracing context
         * (trace/span ids) across the message boundary so a workflow can be followed
         * end-to-end in tools such as Grafana Tempo or Zipkin.
         */
        private boolean observationEnabled = false;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int v) {
            concurrency = v;
        }

        public boolean isMissingTopicsFatal() {
            return missingTopicsFatal;
        }

        public void setMissingTopicsFatal(boolean v) {
            missingTopicsFatal = v;
        }

        public boolean isObservationEnabled() {
            return observationEnabled;
        }

        public void setObservationEnabled(boolean v) {
            observationEnabled = v;
        }
    }
}
