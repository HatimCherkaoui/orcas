package com.github.orcas.demo.controller;

import com.github.orcas.orchestrator.core.annotation.LaunchWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    @PostMapping
    @LaunchWorkflow("order-pipeline")
    public ResponseEntity<Void> create(@RequestBody Map<String,Object> request) {
        return ResponseEntity.accepted().build();
    }
}
