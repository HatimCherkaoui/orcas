package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.resilience.annotation.FallbackStrategy;
import com.github.orcas.orchestrator.resilience.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestCall;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

import java.math.BigDecimal;

@WorkflowRestClient(baseUrl = "${demo.payment.base-url:http://localhost:8089}")
public interface PaymentClient {
    @WorkflowStep("initiate-payment")
    @WorkflowRestCall(mapper = PaymentStartMapper.class, responseSubscriber = PaymentStartResponseConsumer.class)
    @PostExchange("/payments")
    ResponseEntity<PaymentStartResponse> start(@RequestBody PaymentStartRequest request);

    @WorkflowStep("refund-payment")
    @WorkflowCircuitBreaker(fallback = FallbackStrategy.REPLAY)
    @WorkflowRestCall(mapper = PaymentIdMapper.class)
    @PostExchange("/payments/{paymentId}/refund")
    ResponseEntity<PaymentRefundResponse> refund(@PathVariable("paymentId") String paymentId);

    record PaymentStartRequest(Long orderId, BigDecimal amount) {
    }

    record PaymentStartResponse(String paymentId, String status) {
    }

    record PaymentRefundResponse(String paymentId, String status) {
    }
}
