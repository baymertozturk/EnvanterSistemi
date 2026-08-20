package com.example.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retry/DLQ mekanizmasını test etmek için kasıtlı hata tetikleme konfigürasyonu.
 *
 * KULLANIM:
 * 1. POST /test/fail-orders/{orderId} → o orderId'yi "hatalı" olarak işaretle
 * 2. O orderId'ye ait bir event geldiğinde consumer RuntimeException fırlatır
 * 3. @RetryableTopic 3 kez deneyip başarısız olunca mesajı DLT'ye taşır
 */
@Component
@Slf4j
public class FailoverTestConfig {

    private final Set<UUID> failingOrderIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void addFailingOrder(UUID orderId) {
        failingOrderIds.add(orderId);
        log.warn("🔴 [order-service] TEST FLAG SET: orderId={}", orderId);
    }

    public void removeFailingOrder(UUID orderId) {
        failingOrderIds.remove(orderId);
        log.info("🟢 [order-service] TEST FLAG KALDIRILDI: orderId={}", orderId);
    }

    public Set<UUID> getFailingOrderIds() {
        return Collections.unmodifiableSet(failingOrderIds);
    }

    public void throwIfFailing(UUID orderId, String topic) {
        if (orderId != null && failingOrderIds.contains(orderId)) {
            String msg = String.format(
                    "🔴 [order-service] KASITLI TEST HATASI: topic=%s, orderId=%s", topic, orderId);
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }
}
