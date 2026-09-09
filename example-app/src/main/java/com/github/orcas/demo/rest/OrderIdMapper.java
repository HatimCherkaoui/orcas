package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import java.util.Map;

public final class OrderIdMapper implements ContextMapper<String> {
    @Override public String map(StepExecutionContext context) {
        if (context.input() instanceof Map<?, ?> map && map.get("orderId") != null)
            return String.valueOf(map.get("orderId"));
        if (context.workflowContext().businessInput() instanceof Map<?, ?> map && map.get("orderId") != null)
            return String.valueOf(map.get("orderId"));
        return String.valueOf(context.input());
    }
}
