package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public final class OrderIdMapper implements ContextMapper<String> {
    @Override public String map(StepExecutionContext c) {
        if (c.workflowContext().businessInput() instanceof Map<?, ?> m && m.get("orderId") != null) return String.valueOf(m.get("orderId"));
        return String.valueOf(c.input());
    }
}
