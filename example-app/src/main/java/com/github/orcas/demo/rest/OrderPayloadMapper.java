package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public final class OrderPayloadMapper implements ContextMapper<NotificationClient.NotificationRequest> {
    @Override
    public NotificationClient.NotificationRequest map(StepExecutionContext c) {
        Map<?, ?> m = c.workflowContext().businessInput() instanceof Map<?, ?> x ? x : Map.of();
        Object orderValue = c.workflowContext().metadata().get("orderId");
        if (orderValue == null) orderValue = m.get("orderId");
        Object customerValue = c.workflowContext().metadata().get("customerId");
        if (customerValue == null) customerValue = m.get("customerId");
        long orderId = Long.parseLong(String.valueOf(orderValue));
        return new NotificationClient.NotificationRequest(orderId, "NOTIFICATION");
    }
}
