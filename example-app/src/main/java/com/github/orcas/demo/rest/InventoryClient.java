package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestCall;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

@WorkflowRestClient(baseUrl = "${demo.inventory.base-url:http://localhost:8089}")
public interface InventoryClient {
    @WorkflowStep("check-external-inventory")
    @WorkflowRestCall(mapper = OrderIdMapper.class)
    @GetExchange("/inventory/{orderId}")
    ResponseEntity<String> check(@PathVariable String orderId);
}
