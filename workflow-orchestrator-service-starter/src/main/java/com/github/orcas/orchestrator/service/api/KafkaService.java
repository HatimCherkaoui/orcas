package com.github.orcas.orchestrator.service.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view over the Kafka cluster used by the orchestrator, exposed by the
 * dashboard's Kafka page so operators can inspect the {@code workflow.status} topic
 * and consumer group lag without a separate Kafka UI. See {@code DefaultKafkaService}
 * for the {@link org.apache.kafka.clients.admin.AdminClient}-backed implementation.
 */
public interface KafkaService {
    /** Lists the names of all topics visible to the configured admin client, sorted alphabetically. */
    List<String> topics();

    /** Describes a single topic (partition count, replication factor), if it exists. */
    Optional<TopicInfo> topic(String name);

    /** Lists all consumer groups known to the cluster, with their state and committed offsets. */
    List<ConsumerGroupInfo> consumerGroups();

    /** Looks up a single consumer group by id. */
    Optional<ConsumerGroupInfo> consumerGroup(String groupId);

    /** Summary of a Kafka topic's partitioning and replication. */
    record TopicInfo(String name, int partitions, long replicationFactor) {
    }

    /** A consumer group's state and its per-partition committed offsets (keyed as {@code topic-partition}). */
    record ConsumerGroupInfo(String groupId, String state, Map<String, Long> offsets) {
    }
}
