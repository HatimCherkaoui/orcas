package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

@Component
public final class PaymentStartMapper implements ContextMapper<PaymentClient.PaymentStartRequest> {
    @Override
    public PaymentClient.PaymentStartRequest map(StepExecutionContext c) {
        Map<?, ?> m = c.workflowContext().businessInput() instanceof Map<?, ?> x ? x : Map.of();
        Object orderValue = c.workflowContext().metadata().get("orderId");
        if (orderValue == null) orderValue = m.get("orderId");
        Object amountValue = c.workflowContext().metadata().get("amount");
        if (amountValue == null) amountValue = m.get("amount");
        long orderId = Long.parseLong(String.valueOf(orderValue));
        BigDecimal amount = new BigDecimal(String.valueOf(amountValue));
        return new PaymentClient.PaymentStartRequest(orderId, amount);
    }

}
