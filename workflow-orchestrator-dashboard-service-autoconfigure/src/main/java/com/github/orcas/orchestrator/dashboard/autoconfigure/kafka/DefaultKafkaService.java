package com.github.orcas.orchestrator.dashboard.autoconfigure.kafka;

import com.github.orcas.orchestrator.dashboard.api.KafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link KafkaService} implementation backed by the Kafka {@link AdminClient} admin API.
 *
 * <p>Each call opens a short-lived {@code AdminClient} (configured from the Spring Boot
 * {@link KafkaAdmin} bean's bootstrap properties), issues the corresponding admin
 * request, and closes the client again. This keeps the service stateless and safe to
 * use concurrently from the dashboard's REST layer, at the cost of a small per-call
 * connection overhead which is acceptable for a low-traffic operational UI.
 */
public final class DefaultKafkaService implements KafkaService {
    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaService.class);

    private final KafkaAdmin kafkaAdmin;
    private final Duration timeout;

    /**
     * Creates the service.
     *
     * @param kafkaAdmin the Spring Kafka admin bean supplying broker connection properties
     * @param timeout    the maximum time to wait for each admin API call to complete
     */
    public DefaultKafkaService(KafkaAdmin kafkaAdmin, Duration timeout) {
        this.kafkaAdmin = kafkaAdmin;
        this.timeout = timeout;
    }

    private AdminClient client() {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @Override
    public List<String> topics() {
        try (var client = client()) {
            return client.listTopics().names().get(timeout.toMillis(), TimeUnit.MILLISECONDS).stream().sorted().toList();
        } catch (Exception e) {
            log.error("Unable to fetch Kafka topics", e);
            throw new IllegalStateException("Unable to fetch Kafka topics", e);
        }
    }

    @Override
    public Optional<TopicInfo> topic(String name) {
        try (var client = client()) {
            var desc = client.describeTopics(List.of(name)).allTopicNames().get(timeout.toMillis(), TimeUnit.MILLISECONDS).get(name);
            if (desc == null) return Optional.empty();
            var info = new TopicInfo(name, desc.partitions().size(),
                    desc.partitions().stream().mapToLong(p -> p.replicas().size()).max().orElse(0));
            return Optional.of(info);
        } catch (Exception e) {
            log.error("Unable to fetch Kafka topic '{}'", name, e);
            throw new IllegalStateException("Unable to fetch Kafka topic " + name, e);
        }
    }

    @Override
    public List<ConsumerGroupInfo> consumerGroups() {
        try (var client = client()) {
            var groups = client.listConsumerGroups().all().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return groups.stream().map(g -> {
                try {
                    var offsets = client.listConsumerGroupOffsets(g.groupId()).partitionsToOffsetAndMetadata()
                            .get(timeout.toMillis(), TimeUnit.MILLISECONDS)
                            .entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    e -> e.getKey().topic() + "-" + e.getKey().partition(),
                                    e -> e.getValue().offset()));
                    return new ConsumerGroupInfo(g.groupId(), g.groupState().map(Enum::name).orElse("UNKNOWN"), offsets);
                } catch (Exception e) {
                    log.warn("Unable to fetch offsets for consumer group '{}'", g.groupId(), e);
                    return new ConsumerGroupInfo(g.groupId(), "UNKNOWN", Map.of());
                }
            }).toList();
        } catch (Exception e) {
            log.error("Unable to fetch Kafka consumer groups", e);
            throw new IllegalStateException("Unable to fetch Kafka consumer groups", e);
        }
    }

    @Override
    public Optional<ConsumerGroupInfo> consumerGroup(String groupId) {
        return consumerGroups().stream().filter(g -> g.groupId().equals(groupId)).findFirst();
    }
}
