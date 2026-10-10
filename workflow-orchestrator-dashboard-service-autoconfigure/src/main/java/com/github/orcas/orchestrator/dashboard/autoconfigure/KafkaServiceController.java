package com.github.orcas.orchestrator.dashboard.autoconfigure;

import com.github.orcas.orchestrator.dashboard.api.KafkaService;
import com.github.orcas.orchestrator.dashboard.api.KafkaService.ConsumerGroupInfo;
import com.github.orcas.orchestrator.dashboard.api.KafkaService.TopicInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Read-only REST API exposing Kafka cluster introspection (topics and consumer
 * groups) used by the dashboard's Kafka page, backed by {@link KafkaService}.
 */
@RestController
@RequestMapping("${workflow.orchestrator.dashboard.base-path:/api/orchestrator/kafka}")
public final class KafkaServiceController {
    private final KafkaService kafka;

    public KafkaServiceController(KafkaService kafka) {
        this.kafka = kafka;
    }

    @GetMapping("/topics")
    public List<String> topics() { return kafka.topics(); }

    @GetMapping("/topics/{name}")
    public Optional<TopicInfo> topic(@PathVariable("name") String name) { return kafka.topic(name); }

    @GetMapping("/consumer-groups")
    public List<ConsumerGroupInfo> groups() { return kafka.consumerGroups(); }

    @GetMapping("/consumer-groups/{id}")
    public Optional<ConsumerGroupInfo> group(@PathVariable("id") String id) { return kafka.consumerGroup(id); }
}
