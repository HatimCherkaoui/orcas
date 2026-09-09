package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configures the Kafka infrastructure backing the orchestrator: the
 * {@link KafkaWorkflowEventPublisher} and the listener container factory used by
 * {@link WorkflowEventConsumer}, wired with the retry/error-handling behavior from
 * {@link WorkflowKafkaRetryConfiguration}.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
})
@ConditionalOnClass(KafkaTemplate.class)
@Import(WorkflowKafkaRetryConfiguration.class)
public class WorkflowKafkaInfrastructureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    @ConditionalOnBean({KafkaTemplate.class, ObjectMapper.class})
    WorkflowEventPublisher workflowEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            WorkflowProperties properties) {
        return new KafkaWorkflowEventPublisher(kafkaTemplate, objectMapper, properties.getTopic());
    }

    @Bean(name = "workflowKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "workflowKafkaListenerContainerFactory")
    @ConditionalOnBean(ConsumerFactory.class)
    ConcurrentKafkaListenerContainerFactory<Object, Object> workflowKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            CommonErrorHandler workflowKafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(workflowKafkaErrorHandler);
        return factory;
    }
}
