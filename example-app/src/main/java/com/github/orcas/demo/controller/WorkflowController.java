package com.github.orcas.demo.controller;

import com.github.orcas.orchestrator.core.annotation.LaunchWorkflow;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {
    @PostMapping("/order-pipeline")
    @LaunchWorkflow("order-pipeline")
    public ResponseEntity<Void> start(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> body) {
        return ResponseEntity.accepted().build();
    }
}
