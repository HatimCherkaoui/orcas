package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.service.api.KafkaService;
import com.github.orcas.orchestrator.service.api.KafkaService.*;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only REST API exposing Kafka cluster introspection (topics and consumer
 * groups) used by the dashboard's Kafka page, backed by {@link KafkaService}.
 */
@RestController
@RequestMapping("${workflow.orchestrator.service.base-path:/api/orchestrator}/kafka")
public final class KafkaServiceController {
    private final KafkaService kafka;

    public KafkaServiceController(KafkaService kafka) {
        this.kafka = kafka;
    }

    @GetMapping("/topics")
    public java.util.List<String> topics() { return kafka.topics(); }

    @GetMapping("/topics/{name}")
    public java.util.Optional<TopicInfo> topic(@PathVariable("name") String name) { return kafka.topic(name); }

    @GetMapping("/consumer-groups")
    public java.util.List<ConsumerGroupInfo> groups() { return kafka.consumerGroups(); }

    @GetMapping("/consumer-groups/{id}")
    public java.util.Optional<ConsumerGroupInfo> group(@PathVariable("id") String id) { return kafka.consumerGroup(id); }
}
