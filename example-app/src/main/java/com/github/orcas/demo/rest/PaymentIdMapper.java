package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public final class PaymentIdMapper implements ContextMapper<String> {
    @Override public String map(StepExecutionContext c) {
        if (c.workflowContext().businessInput() instanceof Map<?, ?> input
                && input.get("paymentId") != null) {
            return String.valueOf(input.get("paymentId"));
        }
        return String.valueOf(c.input());
    }
}
