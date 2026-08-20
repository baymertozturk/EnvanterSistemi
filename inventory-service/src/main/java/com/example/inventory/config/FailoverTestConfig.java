package com.example.inventory.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retry/DLQ mekanizmasını test etmek için kasıtlı hata tetikleme konfigürasyonu.
 */
@Component
@Slf4j
public class FailoverTestConfig {

    private final Set<UUID> failingOrderIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void addFailingOrder(UUID orderId) {
        failingOrderIds.add(orderId);
        log.warn("🔴 [inventory-service] TEST FLAG SET: orderId={}", orderId);
    }

    public void removeFailingOrder(UUID orderId) {
        failingOrderIds.remove(orderId);
        log.info("🟢 [inventory-service] TEST FLAG KALDIRILDI: orderId={}", orderId);
    }

    public Set<UUID> getFailingOrderIds() {
        return Collections.unmodifiableSet(failingOrderIds);
    }

    public void throwIfFailing(UUID orderId, String topic) {
        if (orderId != null && failingOrderIds.contains(orderId)) {
            String msg = String.format(
                    "🔴 [inventory-service] KASITLI TEST HATASI: topic=%s, orderId=%s", topic, orderId);
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }
}
