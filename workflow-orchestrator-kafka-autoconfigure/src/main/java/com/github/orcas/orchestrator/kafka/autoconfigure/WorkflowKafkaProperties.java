package com.github.orcas.orchestrator.kafka.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka transport settings for workflow status events. */
@ConfigurationProperties("workflow.orchestrator.kafka")
public class WorkflowKafkaProperties {
    private boolean enabled = true;
    private String topic = "workflow.status";
    private String replayTopic = "workflow.replay";
    private String consumerGroup = "workflow-orchestrator";
    private int concurrency = 2;
    private int replayConcurrency = 1;
    private int maxPollRecords = 25;
    private int fetchMaxBytes = 4 * 1024 * 1024;
    private int maxPartitionFetchBytes = 1024 * 1024;
    private boolean consumersEnabled = true;
    private int topicPartitions = 12;
    private short topicReplicationFactor = 1;
    private boolean waitForAcknowledgement = true;
    private boolean missingTopicsFatal = false;
    private boolean observationEnabled = false;
    private Retry retry = new Retry();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getTopic() { return topic; }
    public void setTopic(String value) { topic = value; }
    public String getReplayTopic() { return replayTopic; }
    public void setReplayTopic(String value) { replayTopic = value; }
    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String value) { consumerGroup = value; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int value) { concurrency = positive(value, "concurrency"); }
    public int getReplayConcurrency() { return replayConcurrency; }
    public void setReplayConcurrency(int value) { replayConcurrency = positive(value, "replayConcurrency"); }
    public int getMaxPollRecords() { return maxPollRecords; }
    public void setMaxPollRecords(int value) { maxPollRecords = positive(value, "maxPollRecords"); }
    public int getFetchMaxBytes() { return fetchMaxBytes; }
    public void setFetchMaxBytes(int value) { fetchMaxBytes = positive(value, "fetchMaxBytes"); }
    public int getMaxPartitionFetchBytes() { return maxPartitionFetchBytes; }
    public void setMaxPartitionFetchBytes(int value) { maxPartitionFetchBytes = positive(value, "maxPartitionFetchBytes"); }
    public boolean isConsumersEnabled() { return consumersEnabled; }
    public void setConsumersEnabled(boolean value) { consumersEnabled = value; }
    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
    public int getTopicPartitions() { return topicPartitions; }
    public void setTopicPartitions(int value) { topicPartitions = value; }
    public short getTopicReplicationFactor() { return topicReplicationFactor; }
    public void setTopicReplicationFactor(short value) { topicReplicationFactor = value; }
    public boolean isWaitForAcknowledgement() { return waitForAcknowledgement; }
    public void setWaitForAcknowledgement(boolean value) { waitForAcknowledgement = value; }
    public boolean isMissingTopicsFatal() { return missingTopicsFatal; }
    public void setMissingTopicsFatal(boolean value) { missingTopicsFatal = value; }
    public boolean isObservationEnabled() { return observationEnabled; }
    public void setObservationEnabled(boolean value) { observationEnabled = value; }
    public Retry getRetry() { return retry; }

    /** Retry policy for Kafka listener failures. */
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private java.time.Duration delay = java.time.Duration.ofSeconds(1);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int value) { maxAttempts = value; }
        public java.time.Duration getDelay() { return delay; }
        public void setDelay(java.time.Duration value) { delay = value; }
    }
}
