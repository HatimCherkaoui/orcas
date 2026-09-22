package com.github.orcas.demo.config;

import com.github.orcas.demo.service.OrderService;
import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;

import java.util.Map;
import java.util.stream.Collectors;

public final class PaymentCallbackWorkflow {
    private PaymentCallbackWorkflow() {
    }

    @Workflow("payment-success-callback")
    public static class Success {
        private final OrderService orders;

        public Success(OrderService orders) {
            this.orders = orders;
        }

        @WorkflowStep("confirm-payment")
        public StepResult confirm(StepExecutionContext execution) {
            var input = input(execution);
            orders.confirmPayment(
                    Long.parseLong(String.valueOf(input.get("orderId"))),
                    String.valueOf(input.get("paymentId")));
            return StepResult.success(execution.workflowContext());
        }
    }

    @Workflow("payment-failure-callback")
    public static class Failure {
        private final OrderService orders;

        public Failure(OrderService orders) {
            this.orders = orders;
        }

        @WorkflowStep("fail-payment")
        public StepResult fail(StepExecutionContext execution) {
            var input = input(execution);
            orders.failPayment(
                    Long.parseLong(String.valueOf(input.get("orderId"))),
                    String.valueOf(input.get("paymentId")));
            return StepResult.success(execution.workflowContext());
        }
    }

    @Workflow("payment-refund")
    public static class Refund {
        private final OrderService orders;

        public Refund(OrderService orders) {
            this.orders = orders;
        }

        @WorkflowStep("refund-record")
        public StepResult record(StepExecutionContext execution) {
            var input = input(execution);
            orders.markRefundRequired(
                    Long.parseLong(String.valueOf(input.get("orderId"))),
                    String.valueOf(input.get("paymentId")));
            return StepResult.success(execution.workflowContext());
        }

        @WorkflowStep("refund-completed")
        public StepResult completed(StepExecutionContext execution) {
            var input = input(execution);
            orders.markRefunded(String.valueOf(input.get("paymentId")));
            return StepResult.success(execution.workflowContext());
        }
    }

    private static Map<String, Object> input(StepExecutionContext c) {
        if (c.workflowContext().businessInput() instanceof Map<?, ?> m) {
            return m.entrySet().stream()
                    .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
        }
        return Map.of();
    }
}
