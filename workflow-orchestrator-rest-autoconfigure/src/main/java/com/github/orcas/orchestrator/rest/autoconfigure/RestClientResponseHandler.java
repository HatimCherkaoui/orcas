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
        responseConsumer.consume(execution, entity);
        failOnError(entity);
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
            throw new WorkflowResponseException(
                    stepName,
                    status,
                    categorizer.classifyResponse(status));
        }
    }

    private static boolean containsResponseEntity(Object response) {
        return response instanceof List<?> list
                && !list.isEmpty()
                && list.stream().allMatch(ResponseEntity.class::isInstance);
    }
}
