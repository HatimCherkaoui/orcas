package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestCall;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

@WorkflowRestClient(baseUrl = "${demo.notification.base-url:http://localhost:8089}")
public interface NotificationClient {
    @WorkflowStep(value = "notify-payment-success", async = true)
    @WorkflowRestCall(mapper = OrderPayloadMapper.class)
    @PostExchange("/notifications/payment-success")
    void paymentSuccess(@RequestBody NotificationRequest request);

    @WorkflowStep(value = "notify-payment-failed", async = true)
    @WorkflowRestCall(mapper = OrderPayloadMapper.class)
    @PostExchange("/notifications/payment-failed")
    void paymentFailed(@RequestBody NotificationRequest request);

    record NotificationRequest(Long orderId, String message) {}
}
