package com.example.notification.config;

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
 * 4. Kafka UI'da retry topic'lerde mesajı, son olarak DLT'de görebilirsin
 *
 * DELETE /test/fail-orders/{orderId} → işareti kaldır, normal akışa dön
 */
@Component
@Slf4j
public class FailoverTestConfig {

    /**
     * Thread-safe set — birden fazla iş parçacığı eş zamanlı erişebilir.
     * ConcurrentHashMap.newKeySet() → HashSet'in thread-safe versiyonu.
     */
    private final Set<UUID> failingOrderIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Bu orderId için hata bayrağı set et.
     */
    public void addFailingOrder(UUID orderId) {
        failingOrderIds.add(orderId);
        log.warn("🔴 TEST FLAG SET: orderId={} için kasıtlı hata tetiklenecek. " +
                "Bir sonraki Kafka event'inde @RetryableTopic devreye girecek.", orderId);
    }

    /**
     * Bu orderId için hata bayrağını kaldır.
     */
    public void removeFailingOrder(UUID orderId) {
        failingOrderIds.remove(orderId);
        log.info("🟢 TEST FLAG KALDIRILDI: orderId={} artık normal işlenecek.", orderId);
    }

    /**
     * Aktif hata bayrakları listesi.
     */
    public Set<UUID> getFailingOrderIds() {
        return Collections.unmodifiableSet(failingOrderIds);
    }

    /**
     * Consumer içinden çağrılan kontrol metodu.
     * Eğer bu orderId hatalı listesindeyse RuntimeException fırlatır.
     *
     * @param orderId kontrol edilecek sipariş ID'si
     * @param topic   hangi topic'ten geldiği (log için)
     */
    public void throwIfFailing(UUID orderId, String topic) {
        if (orderId != null && failingOrderIds.contains(orderId)) {
            String msg = String.format(
                    "🔴 KASITLI TEST HATASI: topic=%s, orderId=%s. " +
                    "@RetryableTopic bu mesajı retry queue'ya taşıyacak.", topic, orderId);
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }
}
