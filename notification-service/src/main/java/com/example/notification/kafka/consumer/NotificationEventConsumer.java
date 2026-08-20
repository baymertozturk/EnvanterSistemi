package com.example.notification.kafka.consumer;

import com.example.notification.kafka.event.PaymentCompletedEvent;
import com.example.notification.kafka.event.PaymentFailedEvent;
import com.example.notification.kafka.event.StockRejectedEvent;
import com.example.notification.config.FailoverTestConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Bildirim event'lerini dinleyen consumer.
 * Her işlemde MDC'ye orderId eklenerek JSON loglarda ve distributed tracing'de izlenebilir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final FailoverTestConfig failoverTestConfig;

    // ─────────────────────────────────────────────────────────────
    // payment-completed → Sipariş tamamlandı bildirimi
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "payment-completed", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info(">>> [notification-service] PaymentCompletedEvent alındı: orderId={}", event.getOrderId());

            // Test flag kontrolü — kasıtlı hata tetikle
            failoverTestConfig.throwIfFailing(event.getOrderId(), "payment-completed");

            // Gerçek sistemde burada: e-posta gönder, push notification at, SMS yolla
            log.info("✅ BİLDİRİM [payment-completed]: Sipariş #{} tamamlandı! " +
                    "Müşteriye 'Siparişiniz hazırlandı' e-postası gönderiliyor. (simülasyon)",
                    event.getOrderId());
        }
    }

    @DltHandler
    public void handlePaymentCompletedDlt(PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: PaymentCompletedEvent işlenemedi! orderId={}. " +
                    "Manuel inceleme gerekiyor.", topic, event.getOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // payment-failed → Ödeme başarısız bildirimi
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "payment-failed", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info(">>> [notification-service] PaymentFailedEvent alındı: orderId={}, reason={}",
                    event.getOrderId(), event.getReason());

            failoverTestConfig.throwIfFailing(event.getOrderId(), "payment-failed");

            log.warn("❌ BİLDİRİM [payment-failed]: Sipariş #{} için ödeme başarısız! " +
                    "Müşteriye 'Ödemeniz reddedildi' e-postası gönderiliyor. Sebep: {}. (simülasyon)",
                    event.getOrderId(), event.getReason());
        }
    }

    @DltHandler
    public void handlePaymentFailedDlt(PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: PaymentFailedEvent işlenemedi! orderId={}. " +
                    "Manuel inceleme gerekiyor.", topic, event.getOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // stock-rejected → Stok yetersiz bildirimi
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "stock-rejected", groupId = "${spring.kafka.consumer.group-id}")
    public void handleStockRejected(StockRejectedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info(">>> [notification-service] StockRejectedEvent alındı: orderId={}, reason={}",
                    event.getOrderId(), event.getReason());

            failoverTestConfig.throwIfFailing(event.getOrderId(), "stock-rejected");

            log.warn("📦 BİLDİRİM [stock-rejected]: Sipariş #{} için yeterli stok yok! " +
                    "Müşteriye 'Stok tükendi' e-postası gönderiliyor. Sebep: {}. (simülasyon)",
                    event.getOrderId(), event.getReason());
        }
    }

    @DltHandler
    public void handleStockRejectedDlt(StockRejectedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: StockRejectedEvent işlenemedi! orderId={}. " +
                    "Manuel inceleme gerekiyor.", topic, event.getOrderId());
        }
    }
}
