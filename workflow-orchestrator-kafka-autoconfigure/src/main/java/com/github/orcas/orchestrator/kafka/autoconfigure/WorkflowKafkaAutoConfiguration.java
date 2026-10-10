package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.service.api.WorkflowReplayPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import tools.jackson.databind.ObjectMapper;

/** Kafka publisher and consumer for workflow status events. */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "workflow.orchestrator.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowKafkaProperties.class)
public final class WorkflowKafkaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    WorkflowEventPublisher kafkaWorkflowEventPublisher(
            KafkaTemplate<String, String> template,
            org.springframework.beans.factory.ObjectProvider<ObjectMapper> mapperProvider,
            WorkflowKafkaProperties properties) {
        return new KafkaWorkflowEventPublisher(template, mapperProvider.getObject(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowReplayPublisher.class)
    WorkflowReplayPublisher kafkaWorkflowReplayPublisher(
            KafkaTemplate<String, String> template,
            ObjectMapper mapper,
            WorkflowKafkaProperties properties) {
        return new KafkaWorkflowReplayPublisher(template, mapper, properties);
    }

    @Bean
    NewTopic workflowStatusTopic(WorkflowKafkaProperties properties) {
        return new NewTopic(properties.getTopic(), properties.getTopicPartitions(), properties.getTopicReplicationFactor());
    }

    @Bean
    NewTopic workflowReplayTopic(WorkflowKafkaProperties properties) {
        return new NewTopic(properties.getReplayTopic(), properties.getTopicPartitions(), properties.getTopicReplicationFactor());
    }

    @Bean
    NewTopic workflowStatusDeadLetterTopic(WorkflowKafkaProperties properties) {
        return new NewTopic(properties.getTopic() + ".DLT", properties.getTopicPartitions(), properties.getTopicReplicationFactor());
    }

    @Bean
    NewTopic workflowReplayDeadLetterTopic(WorkflowKafkaProperties properties) {
        return new NewTopic(properties.getReplayTopic() + ".DLT", properties.getTopicPartitions(), properties.getTopicReplicationFactor());
    }

    @Bean(name = "workflowKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String>
    workflowKafkaListenerContainerFactory(
            ConsumerFactory<String, String> factory,
            KafkaTemplate<String, String> template,
            WorkflowKafkaProperties properties,
            org.springframework.beans.factory.ObjectProvider<io.micrometer.observation.ObservationRegistry> observations) {
        var listener = new ConcurrentKafkaListenerContainerFactory<String, String>();
        listener.setConsumerFactory(factory);
        listener.getContainerProperties().setObservationEnabled(true);
        listener.getContainerProperties().setObservationRegistry(observations.getIfAvailable(() -> io.micrometer.observation.ObservationRegistry.NOOP));
        listener.setConcurrency(properties.getConcurrency());
        listener.getContainerProperties().setMissingTopicsFatal(properties.isMissingTopicsFatal());
        long retries = properties.getRetry().isEnabled()
                ? Math.max(0, properties.getRetry().getMaxAttempts() - 1L)
                : 0;
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        listener.setCommonErrorHandler(new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(Math.max(0, properties.getRetry().getDelay().toMillis()), retries)));
        return listener;
    }

}
