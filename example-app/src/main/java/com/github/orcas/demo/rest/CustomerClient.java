package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClient;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

@WorkflowRestClient(baseUrl = "${demo.customer.base-url:http://localhost:8089}")
public interface CustomerClient {
    @WorkflowStep(value = "customer-call", mapper = OrderIdMapper.class)
    @GetExchange("/customers/{id}")
    ResponseEntity<String> get(@PathVariable("id") String id);
}
