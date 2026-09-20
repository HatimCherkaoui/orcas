package com.github.orcas.demo.rest;

import com.github.orcas.orchestrator.autoconfigure.rest.WorkflowRestClient;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

@WorkflowRestClient(baseUrl = "${demo.retry.base-url:http://localhost:8089}")
public interface RetryDemoClient {
    @WorkflowStep(value = "retry-success-call", mapper = RetryScenarioMapper.class)
    @GetExchange("/retrydemo/{scenario}/success/{id}")
    ResponseEntity<String> retrySuccess(@PathVariable("scenario") String scenario, @PathVariable("id") String id);

    @WorkflowStep(value = "retry-suspend-call", mapper = RetryScenarioMapper.class)
    @GetExchange("/retrydemo/{scenario}/suspend/{id}")
    ResponseEntity<String> retrySuspend(@PathVariable("scenario") String scenario, @PathVariable("id") String id);
}

