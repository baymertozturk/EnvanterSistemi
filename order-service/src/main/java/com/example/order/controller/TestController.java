package com.example.order.controller;

import com.example.order.config.FailoverTestConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Retry/DLQ mekanizmasını manuel test etmek için endpoint'ler.
 * Detaylı açıklama için notification-service/TestController.java'ya bak.
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
                "service", "order-service",
                "status", "FAIL_FLAG_SET",
                "orderId", orderId,
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }

    @DeleteMapping("/fail-orders/{orderId}")
    public ResponseEntity<Map<String, Object>> removeFailingOrder(@PathVariable UUID orderId) {
        failoverTestConfig.removeFailingOrder(orderId);
        return ResponseEntity.ok(Map.of(
                "service", "order-service",
                "status", "FAIL_FLAG_REMOVED",
                "orderId", orderId,
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }

    @GetMapping("/fail-orders")
    public ResponseEntity<Map<String, Object>> listFailingOrders() {
        return ResponseEntity.ok(Map.of(
                "service", "order-service",
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }
}
