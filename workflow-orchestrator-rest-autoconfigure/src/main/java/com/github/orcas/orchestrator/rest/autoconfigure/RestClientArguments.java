package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;

/** Maps a REST method signature to arguments from the workflow execution context. */
final class RestClientArguments {
    private RestClientArguments() {
    }

    static Object[] resolve(Method method, Object mapped, StepExecutionContext execution) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }
        if (parameters.length == 1) {
            return new Object[]{argument(parameters[0], mapped, execution)};
        }
        return Arrays.stream(parameters)
                .map(parameter -> argument(parameter, mapped, execution))
                .toArray();
    }

    private static Object argument(Parameter parameter, Object mapped, StepExecutionContext execution) {
        var path = parameter.getAnnotation(PathVariable.class);
        if (path != null) {
            return value(argumentName(path.value(), path.name(), parameter), mapped, execution);
        }

        var header = parameter.getAnnotation(RequestHeader.class);
        if (header != null) {
            String key = argumentName(header.value(), header.name(), parameter);
            return execution.workflowContext().metadata().get(key);
        }

        if (parameter.isAnnotationPresent(RequestBody.class)) {
            return mapped;
        }

        return mapped;
    }

    /**
     * Resolves a path-variable value with explicit mapper output taking precedence over
     * execution data. A mapped map is keyed by the path-variable name; a mapped scalar is
     * the value itself. Only when no mapped value exists do metadata and workflow input act
     * as fallbacks.
     */
    private static Object value(String key, Object mapped, StepExecutionContext execution) {
        if (!key.isBlank() && mapped instanceof Map<?, ?> map && map.containsKey(key)) {
            return map.get(key);
        }

        // A mapper may intentionally resolve a path variable to a scalar.
        // Preserve that explicit mapping before falling back to workflow data.
        if (mapped != null && !(mapped instanceof Map<?, ?>)) {
            return mapped;
        }

        if (!key.isBlank()) {
            var metadataValue = execution.workflowContext().metadata().get(key);
            if (metadataValue != null) {
                return metadataValue;
            }

            if (execution.input() instanceof Map<?, ?> map && map.containsKey(key)) {
                return map.get(key);
            }
        }

        return mapped != null ? mapped : execution.input();
    }

    private static String argumentName(String value, String name, Parameter parameter) {
        if (!value.isBlank()) {
            return value;
        }
        if (!name.isBlank()) {
            return name;
        }
        return parameter.getName();
    }
}
