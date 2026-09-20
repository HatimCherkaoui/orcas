package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowStateStore;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.retry.WorkflowRetryableException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
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
 * needing manual intervention instead of silently dropping the message. Every
 * intermediate failed delivery attempt (i.e. every retry) is also recorded through
 * {@link WorkflowStateStore#recordRetry} - when a state store bean is available -
 * so the dashboard can show a per-step retry count and audit trail.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KafkaTemplate.class)
public class WorkflowKafkaRetryConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowKafkaRetryConfiguration.class);

    @Bean(name = "workflowKafkaErrorHandler")
    @ConditionalOnMissingBean(name = "workflowKafkaErrorHandler")
    CommonErrorHandler workflowKafkaErrorHandler(WorkflowRetryProperties properties, WorkflowEventPublisher publisher,
            ObjectMapper mapper, ObjectProvider<WorkflowStateStore> stateStoreProvider) {
        properties.validate();
        ConsumerRecordRecoverer recoverer = (record, exception) -> suspend(record, exception, publisher, mapper);
        var handler = new DefaultErrorHandler(recoverer, properties.isEnabled() ? defaultBackOff(properties) : new FixedBackOff(0L, 0L));
        handler.addRetryableExceptions(WorkflowRetryableException.class);
        handler.setBackOffFunction((ConsumerRecord<?, ?> record, Exception exception) -> backOff(properties, exception));
        handler.setRetryListeners((record, exception, deliveryAttempt) ->
                recordRetry(record, exception, deliveryAttempt, stateStoreProvider, mapper));
        return handler;
    }

    /**
     * Invoked by Spring Kafka's {@link DefaultErrorHandler} after every failed delivery
     * attempt that will be retried (i.e. before each retry, not just once at the end).
     * Persists the attempt via {@link WorkflowStateStore#recordRetry} if a state store
     * bean is configured; silently does nothing otherwise (e.g. no JDBC state store, or
     * the record isn't a well-formed {@link StatusEvent}).
     */
    private void recordRetry(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt,
            ObjectProvider<WorkflowStateStore> stateStoreProvider, ObjectMapper mapper) {
        WorkflowStateStore store = stateStoreProvider.getIfAvailable();
        if (store == null) return;
        try {
            StatusEvent event = mapper.readValue(String.valueOf(record.value()), StatusEvent.class);
            // The consumed record carries the *triggering* event (e.g. "join SUCCESS"),
            // but what is actually being retried is the step that threw. Prefer the
            // failing step name reported by the WorkflowRetryableException so the retry
            // is attributed to the step operators see failing on the dashboard.
            String step = failingStep(exception, event.step());
            store.recordRetry(event.workflowId(), step, deliveryAttempt, rootMessage(exception));
        } catch (Exception ignored) {
        }
    }

    /**
     * Walks the cause chain looking for a {@link WorkflowRetryableException} and returns
     * the step name it reports, falling back to {@code fallback} when the failure did not
     * originate from a recognizable workflow step.
     */
    private String failingStep(Throwable error, String fallback) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WorkflowRetryableException retryable && retryable.step() != null) {
                return retryable.step();
            }
            if (t.getCause() == t) break;
        }
        return fallback;
    }

    private void suspend(ConsumerRecord<?, ?> record, Exception exception, WorkflowEventPublisher publisher, ObjectMapper mapper) {
        try {
            StatusEvent event = mapper.readValue(String.valueOf(record.value()), StatusEvent.class);
            String step = failingStep(exception, event.step());
            if (event.status() == Status.SUSPENDED) {
                log.warn("Exhausted retries for already suspended workflow instance {} step '{}'; not publishing another SUSPENDED event: {}",
                        event.workflowId(), step, rootMessage(exception));
                return;
            }
            log.warn("Exhausted retries for workflow instance {} step '{}'; suspending: {}",
                    event.workflowId(), step, rootMessage(exception));
            publisher.publish(StatusEvent.of(event.workflowId(), event.workflow(), step, Status.SUSPENDED,
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

