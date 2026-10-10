package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.retry.WorkflowResponseException;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Converts an HTTP response into workflow output and response metadata. */
final class RestClientResponseHandler {
    private final String stepName;
    private final WorkflowErrorCategorizer categorizer;
    private final ResponseConsumer<Object> responseConsumer;

    RestClientResponseHandler(
            Method method,
            WorkflowErrorCategorizer categorizer,
            ResponseConsumer<Object> responseConsumer) {
        this.stepName = StepNames.of(method);
        this.categorizer = categorizer;
        this.responseConsumer = responseConsumer;
    }

    StepResult handle(WorkflowContext context, StepExecutionContext execution, Object response) {
        if (response instanceof ResponseEntity<?> entity) {
            return handleEntity(context, execution, entity);
        }
        if (containsResponseEntity(response)) {
            return handleEntities(context, execution, (List<?>) response);
        }

        execution.output(response);
        return StepResult.success(context);
    }

    private StepResult handleEntity(
            WorkflowContext context,
            StepExecutionContext execution,
            ResponseEntity<?> entity) {
        recordResponse(execution, entity);
        failOnError(entity);
        responseConsumer.consume(execution, entity);
        execution.output(entity.getBody());
        return StepResult.success(context);
    }

    private StepResult handleEntities(
            WorkflowContext context,
            StepExecutionContext execution,
            List<?> responses) {
        var entities = responses.stream()
                .map(item -> (ResponseEntity<?>) item)
                .toList();
        entities.forEach(this::failOnError);
        var bodies = entities.stream().map(ResponseEntity::getBody).toList();
        responseConsumer.consume(execution, responses);
        execution.output(bodies);
        return StepResult.success(context);
    }

    private void recordResponse(StepExecutionContext execution, ResponseEntity<?> entity) {
        int status = entity.getStatusCode().value();
        execution.workflowContext().metadata().put("http.response.status", Integer.toString(status));
        entity.getHeaders().forEach((key, values) -> {
            var value = String.join(",", values);
            execution.workflowContext().metadata().put("http.response.header." + key, value);
            execution.attribute("http.header." + key, value);
        });
        execution.attribute("http.status", status);
    }

    private void failOnError(ResponseEntity<?> entity) {
        if (entity.getStatusCode().isError()) {
            int status = entity.getStatusCode().value();
            String responseMessage = briefMessage(entity.getBody());
            Duration retryAfter = retryAfter(entity.getHeaders().getFirst("Retry-After"));
            throw new WorkflowResponseException(
                    stepName,
                    status,
                    categorizer.classifyResponse(status, responseMessage, retryAfter));
        }
    }

    private static Duration retryAfter(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return Duration.ofSeconds(Math.max(0, seconds));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (RuntimeException invalidDate) {
                return null;
            }
        }
    }

    private static String briefMessage(Object body) {
        if (body == null) return null;
        String message = body.toString().trim();
        if (message.isEmpty()) return null;
        return message.length() > 300 ? message.substring(0, 297) + "..." : message;
    }

    private static boolean containsResponseEntity(Object response) {
        return response instanceof List<?> list
                && !list.isEmpty()
                && list.stream().allMatch(ResponseEntity.class::isInstance);
    }
}
