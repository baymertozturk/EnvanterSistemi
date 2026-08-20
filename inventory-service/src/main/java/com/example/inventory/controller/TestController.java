package com.example.inventory.controller;

import com.example.inventory.config.FailoverTestConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Retry/DLQ mekanizmasını manuel test etmek için endpoint'ler.
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final FailoverTestConfig failoverTestConfig;

    @PostMapping("/fail-orders/{orderId}")
    public ResponseEntity<Map<String, Object>> addFailingOrder(@PathVariable UUID orderId) {
        failoverTestConfig.addFailingOrder(orderId);
        return ResponseEntity.ok(Map.of(
                "service", "inventory-service",
                "status", "FAIL_FLAG_SET",
                "orderId", orderId,
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }

    @DeleteMapping("/fail-orders/{orderId}")
    public ResponseEntity<Map<String, Object>> removeFailingOrder(@PathVariable UUID orderId) {
        failoverTestConfig.removeFailingOrder(orderId);
        return ResponseEntity.ok(Map.of(
                "service", "inventory-service",
                "status", "FAIL_FLAG_REMOVED",
                "orderId", orderId,
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }

    @GetMapping("/fail-orders")
    public ResponseEntity<Map<String, Object>> listFailingOrders() {
        return ResponseEntity.ok(Map.of(
                "service", "inventory-service",
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }
}
