package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

/**
 * Configures the Kafka {@link CommonErrorHandler} applied to the workflow status-event
 * listener: retries {@link WorkflowRetryableException}s using a per-step-aware
 * backoff derived from {@link WorkflowRetryProperties}, and once attempts are
 * exhausted, publishes a {@code SUSPENDED} status event so the workflow surfaces as
 * needing manual intervention instead of silently dropping the message.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KafkaTemplate.class)
public class WorkflowKafkaRetryConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowKafkaRetryConfiguration.class);

    @Bean(name = "workflowKafkaErrorHandler")
    @ConditionalOnMissingBean(name = "workflowKafkaErrorHandler")
    CommonErrorHandler workflowKafkaErrorHandler(WorkflowRetryProperties properties, WorkflowEventPublisher publisher, ObjectMapper mapper) {
        properties.validate();
        ConsumerRecordRecoverer recoverer = (record, exception) -> suspend(record, exception, publisher, mapper);
        var handler = new DefaultErrorHandler(recoverer, properties.isEnabled() ? defaultBackOff(properties) : new FixedBackOff(0L, 0L));
        handler.addRetryableExceptions(WorkflowRetryableException.class);
        handler.setBackOffFunction((ConsumerRecord<?, ?> record, Exception exception) -> backOff(properties, exception));
        return handler;
    }

    private void suspend(ConsumerRecord<?, ?> record, Exception exception, WorkflowEventPublisher publisher, ObjectMapper mapper) {
        try {
            StatusEvent event = mapper.readValue(String.valueOf(record.value()), StatusEvent.class);
            log.warn("Exhausted retries for workflow instance {} step '{}'; suspending: {}",
                    event.workflowId(), event.step(), rootMessage(exception));
            publisher.publish(StatusEvent.of(event.workflowId(), event.workflow(), event.step(), Status.SUSPENDED,
                    event.metadata(), "automatic replay exhausted: " + rootMessage(exception)));
        } catch (Exception ignored) {
        }
    }

    private String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }

    private FixedBackOff defaultBackOff(WorkflowRetryProperties p) {
        return new FixedBackOff(p.getDelay().toMillis(), Math.max(0, p.getMaxAttempts() - 1L));
    }

    private FixedBackOff backOff(WorkflowRetryProperties p, Exception exception) {
        if (exception instanceof WorkflowRetryableException retryable) {
            var step = p.getSteps().get(retryable.step());
            if (step != null) {
                int attempts = step.getMaxAttempts() != null ? step.getMaxAttempts() : p.getMaxAttempts();
                var delay = step.getDelay() != null ? step.getDelay() : p.getDelay();
                return new FixedBackOff(delay.toMillis(), Math.max(0, attempts - 1L));
            }
        }
        return defaultBackOff(p);
    }

}
