package com.github.orcas.demo.rest;

import com.github.orcas.demo.domain.Payment;
import com.github.orcas.demo.domain.PaymentStatus;
import com.github.orcas.demo.repository.OrderRepository;
import com.github.orcas.demo.repository.PaymentRepository;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PaymentStartResponseConsumer implements ResponseConsumer<ResponseEntity<PaymentClient.PaymentStartResponse>> {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentStartResponseConsumer(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }


    @Override
    public void consume(StepExecutionContext c, ResponseEntity<PaymentClient.PaymentStartResponse> response) {
        Map<?, ?> m = c.workflowContext().businessInput() instanceof Map<?, ?> x ? x : Map.of();
        Object orderValue = c.workflowContext().metadata().get("orderId");
        if (orderValue == null) orderValue = m.get("orderId");
        Object amountValue = c.workflowContext().metadata().get("amount");
        if (amountValue == null) amountValue = m.get("amount");
        long orderId = Long.parseLong(String.valueOf(orderValue));
        BigDecimal amount = new BigDecimal(String.valueOf(amountValue));
        orderRepository.findById(orderId).ifPresent(order -> {
            Payment payment = new Payment(
                    order,
                    amount,
                    PaymentStatus.valueOf(response.getBody().status()),
                    response.getBody().paymentId()
            );
            paymentRepository.save(payment);
        });
    }
}
