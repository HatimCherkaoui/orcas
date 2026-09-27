package com.github.orcas.orchestrator.dashboard.autoconfigure;
import org.springframework.kafka.core.KafkaAdmin;
public class KafkaServiceController {
    private final KafkaAdmin kafkaAdmin;
    public KafkaServiceController(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }
}
