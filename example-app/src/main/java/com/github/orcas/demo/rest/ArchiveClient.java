package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClient;
import com.github.orcas.orchestrator.core.annotation.FallbackStrategy;
import com.github.orcas.orchestrator.core.annotation.WorkflowCircuitBreaker;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

@WorkflowRestClient(baseUrl = "${demo.archive.base-url:http://localhost:8089}")
public interface ArchiveClient {
    @WorkflowStep(value = "archive-call", mapper = OrderIdMapper.class)
    @WorkflowCircuitBreaker(name = "archive-call", fallback = FallbackStrategy.REPLAY)
    @GetExchange("/archive/{id}")
    ResponseEntity<String> get(@PathVariable("id") String id);
}
