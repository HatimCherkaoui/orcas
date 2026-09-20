package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClient;
import com.github.orcas.orchestrator.core.annotation.FallbackStrategy;
import com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

@WorkflowRestClient(baseUrl = "${demo.veryinstableapi.base-url:http://localhost:8089}")
public interface VeryInstableApiClient {
    @WorkflowStep(value = "veryinstableapi-call", mapper = RetryScenarioMapper.class)
    @WorkflowCircuitBreaker(name = "veryinstableapi-call", fallback = FallbackStrategy.REPLAY)
    @GetExchange("/veryinstableendpoint/{scenario}/{id}")
    ResponseEntity<String> get(@PathVariable("scenario") String scenario, @PathVariable("id") String id);
}
