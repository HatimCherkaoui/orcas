package com.github.orcas.demo.controller;

import com.github.orcas.orchestrator.core.annotation.LaunchWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/payments/callback")
public class PaymentCallbackController {
    @PostMapping("/success")
    @LaunchWorkflow("payment-success-callback")
    public ResponseEntity<Void> success(@RequestBody Map<String,Object> callback) {
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/failed")
    @LaunchWorkflow("payment-failure-callback")
    public ResponseEntity<Void> failed(@RequestBody Map<String,Object> callback) {
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/refund")
    @LaunchWorkflow("payment-refund")
    public ResponseEntity<Void> refund(@RequestBody Map<String,Object> callback) {
        return ResponseEntity.accepted().build();
    }
}
