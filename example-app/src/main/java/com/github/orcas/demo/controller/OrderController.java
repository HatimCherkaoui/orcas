package com.github.orcas.demo.controller;

import com.github.orcas.orchestrator.rest.annotation.LaunchWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    @PostMapping
    @LaunchWorkflow(workflow = com.github.orcas.demo.config.OrderWorkflow.class)
    public ResponseEntity<Void> create(@RequestBody Map<String,Object> request) {
        return ResponseEntity.accepted().build();
    }
}
