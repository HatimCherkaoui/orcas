package com.github.orcas.demo.controller;

import com.github.orcas.orchestrator.rest.annotation.LaunchWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/payments/callback")
public class PaymentCallbackController {
    @PostMapping("/success")
    @LaunchWorkflow(workflow = com.github.orcas.demo.config.PaymentCallbackWorkflow.Success.class)
    public ResponseEntity<Void> success(@RequestBody Map<String,Object> callback) {
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/failed")
    @LaunchWorkflow(workflow = com.github.orcas.demo.config.PaymentCallbackWorkflow.Failure.class)
    public ResponseEntity<Void> failed(@RequestBody Map<String,Object> callback) {
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/refund")
    @LaunchWorkflow(workflow = com.github.orcas.demo.config.PaymentCallbackWorkflow.Refund.class)
    public ResponseEntity<Void> refund(@RequestBody Map<String,Object> callback) {
        return ResponseEntity.accepted().build();
    }
}
