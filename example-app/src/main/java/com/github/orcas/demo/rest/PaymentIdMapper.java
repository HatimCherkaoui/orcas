package com.github.orcas.demo.rest;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import java.util.Map;
public final class PaymentIdMapper implements ContextMapper<String> {
    @Override public String map(StepExecutionContext c) {
        if (c.workflowContext().businessInput() instanceof Map<?,?> m && m.get("paymentId") != null) return String.valueOf(m.get("paymentId"));
        return String.valueOf(c.input());
    }
}
