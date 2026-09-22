package com.github.orcas.demo.config;

import com.github.orcas.demo.service.OrderService;
import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Workflow("order-pipeline")
public class OrderWorkflow {
    private final OrderService orders;

    public OrderWorkflow(OrderService orders) {
        this.orders = orders;
    }

    @WorkflowStep("validate-and-reserve")
    public StepResult validateAndReserve(StepExecutionContext execution) {
        Map<String, Object> input = businessInput(execution);
        long customerId = Long.parseLong(String.valueOf(input.get("customerId")));
        var items = ((java.util.List<?>) input.get("items")).stream()
                .map(x -> {
                    Map<?, ?> i = (Map<?, ?>) x;
                    return new OrderService.CreateOrderItem(
                            String.valueOf(i.get("sku")),
                            Integer.parseInt(String.valueOf(i.get("quantity"))),
                            new BigDecimal(String.valueOf(i.get("unitPrice")))
                    );
                })
                .toList();

        var order = orders.createPendingOrder(
                new OrderService.CreateOrderCommand(customerId, items));

        execution.workflowContext().metadata().put("orderId", String.valueOf(order.getId()));
        execution.workflowContext().metadata().put("amount", order.getTotalAmount().toPlainString());
        return StepResult.success(execution.workflowContext());
    }

    @WorkflowStep("load-order")
    public StepResult loadOrder(StepExecutionContext execution) {
        long orderId = Long.parseLong(value(execution, "orderId"));
        var order = orders.get(orderId);
        execution.workflowContext().metadata().put("amount", order.getTotalAmount().toPlainString());
        return StepResult.success(execution.workflowContext());
    }

    private static Map<String, Object> businessInput(StepExecutionContext c) {
        if (c.workflowContext().businessInput() instanceof Map<?, ?> m) {
            return m.entrySet().stream()
                    .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
        }
        return Map.of();
    }

    private static String value(StepExecutionContext c, String key) {
        Object v = c.workflowContext().metadata().get(key);
        if (v == null) v = businessInput(c).get(key);
        return String.valueOf(v);
    }
}
