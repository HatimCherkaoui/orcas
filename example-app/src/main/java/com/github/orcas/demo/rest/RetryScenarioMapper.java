package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts the demo scenario and workflow business id from the original launch input so
 * every retry/circuit-breaker demo step can route to its matching WireMock behavior.
 */
public final class RetryScenarioMapper implements ContextMapper<Map<String, String>> {
    @Override
    public Map<String, String> map(StepExecutionContext context) {
        Map<?, ?> source = source(context);
        String scenario = stringValue(source, "scenario", "happy");
        String id = stringValue(source, "orderId", stringValue(source, "id", String.valueOf(context.input())));

        Map<String, String> mapped = new LinkedHashMap<>();
        mapped.put("scenario", scenario == null || scenario.isBlank() ? "happy" : scenario);
        mapped.put("id", id == null || id.isBlank() ? "unknown" : id);
        return mapped;
    }

    private Map<?, ?> source(StepExecutionContext context) {
        if (context.workflowContext().businessInput() instanceof Map<?, ?> businessInput) {
            return businessInput;
        }
        if (context.input() instanceof Map<?, ?> input) {
            return input;
        }
        return Map.of();
    }

    private String stringValue(Map<?, ?> source, String key, String fallback) {
        Object value = source.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}

