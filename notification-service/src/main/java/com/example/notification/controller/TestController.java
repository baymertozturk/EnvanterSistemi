package com.example.notification.controller;

import com.example.notification.config.FailoverTestConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Retry/DLQ mekanizmasını manuel test etmek için endpoint'ler.
 *
 * KULLANIM AKIŞI:
 * ───────────────
 * 1) POST /test/fail-orders/{orderId}
 *    → Bu orderId'yi "hata listesine" ekle
 *
 * 2) Kafka'ya o orderId'li bir event gönder
 *    (Sipariş oluştur veya Kafka UI'dan manuel produce et)
 *
 * 3) Consumer exception fırlatır → @RetryableTopic devreye girer:
 *    payment-completed → payment-completed-retry-0 → retry-1 → retry-2 → payment-completed-dlt
 *
 * 4) Kafka UI'da (localhost:8080) retry topic'leri ve DLT'yi izle
 *
 * 5) DELETE /test/fail-orders/{orderId}
 *    → Bayrağı kaldır, normal akışa dön
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final FailoverTestConfig failoverTestConfig;

    /**
     * Belirtilen orderId için hata bayrağı set et.
     * Bundan sonraki Kafka event'lerinde kasıtlı exception fırlatılır.
     */
    @PostMapping("/fail-orders/{orderId}")
    public ResponseEntity<Map<String, Object>> addFailingOrder(@PathVariable UUID orderId) {
        failoverTestConfig.addFailingOrder(orderId);
        return ResponseEntity.ok(Map.of(
                "status", "FAIL_FLAG_SET",
                "orderId", orderId,
                "message", "Bu orderId için bir sonraki Kafka event'i kasıtlı hata üretecek. " +
                           "@RetryableTopic → retry-0 → retry-1 → retry-2 → DLT zinciri başlayacak.",
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }

    /**
     * Hata bayrağını kaldır.
     */
    @DeleteMapping("/fail-orders/{orderId}")
    public ResponseEntity<Map<String, Object>> removeFailingOrder(@PathVariable UUID orderId) {
        failoverTestConfig.removeFailingOrder(orderId);
        return ResponseEntity.ok(Map.of(
                "status", "FAIL_FLAG_REMOVED",
                "orderId", orderId,
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds()
        ));
    }

    /**
     * Aktif hata bayraklarını listele.
     */
    @GetMapping("/fail-orders")
    public ResponseEntity<Map<String, Object>> listFailingOrders() {
        return ResponseEntity.ok(Map.of(
                "activeFailingOrders", failoverTestConfig.getFailingOrderIds(),
                "count", failoverTestConfig.getFailingOrderIds().size()
        ));
    }
}
