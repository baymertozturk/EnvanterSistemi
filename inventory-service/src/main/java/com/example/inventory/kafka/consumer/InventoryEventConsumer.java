package com.example.inventory.kafka.consumer;

import com.example.inventory.config.FailoverTestConfig;
import com.example.inventory.kafka.event.OrderCreatedEvent;
import com.example.inventory.kafka.event.PaymentFailedEvent;
import com.example.inventory.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka event consumer — inventory-service.
 * Her işlemde MDC'ye orderId eklenerek JSON loglarda ve distributed tracing'de izlenebilir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final StockReservationService stockReservationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final FailoverTestConfig failoverTestConfig;

    private static final String PROCESSED_KEY_PREFIX = "processed:";
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    // ─────────────────────────────────────────────────────────────
    // order-created → stok rezervasyonu
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "order-created", groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info("OrderCreatedEvent alındı: eventId={}, orderId={}, productId={}, quantity={}",
                    event.getEventId(), event.getOrderId(), event.getProductId(), event.getQuantity());

            // Test flag kontrolü — kasıtlı hata tetikle (retry zincirini başlatır)
            failoverTestConfig.throwIfFailing(event.getOrderId(), "order-created");

            // İdempotency: retry durumunda aynı event tekrar işlenmez
            if (event.getEventId() != null) {
                String redisKey = PROCESSED_KEY_PREFIX + event.getEventId().toString();
                Boolean alreadyProcessed = redisTemplate.hasKey(redisKey);
                if (Boolean.TRUE.equals(alreadyProcessed)) {
                    log.warn("Duplicate event ignored: eventId={}, orderId={}.",
                            event.getEventId(), event.getOrderId());
                    return;
                }
            }

            stockReservationService.reserveStock(event);

            if (event.getEventId() != null) {
                String redisKey = PROCESSED_KEY_PREFIX + event.getEventId().toString();
                redisTemplate.opsForValue().set(redisKey, "true", PROCESSED_TTL);
                log.info("Event Redis'e kaydedildi: key={}, TTL=24h", redisKey);
            }
        }
    }

    @DltHandler
    public void handleOrderCreatedDlt(OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: OrderCreatedEvent işlenemedi! orderId={}. " +
                    "Stok rezervasyonu yapılamadı — manuel inceleme gerekiyor.", topic, event.getOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // payment-failed → Saga compensating transaction
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
            log.warn("PaymentFailedEvent alındı: eventId={}, orderId={}, productId={}, quantity={}",
                    event.getEventId(), event.getOrderId(), event.getProductId(), event.getQuantity());

            failoverTestConfig.throwIfFailing(event.getOrderId(), "payment-failed");

            // İdempotency: compensate işlemi de idempotent olmalı
            if (event.getEventId() != null) {
                String redisKey = PROCESSED_KEY_PREFIX + "compensate:" + event.getEventId().toString();
                Boolean alreadyProcessed = redisTemplate.hasKey(redisKey);
                if (Boolean.TRUE.equals(alreadyProcessed)) {
                    log.warn("Duplicate compensate event ignored: eventId={}, orderId={}.",
                            event.getEventId(), event.getOrderId());
                    return;
                }
            }

            stockReservationService.compensateReservation(event);

            if (event.getEventId() != null) {
                String redisKey = PROCESSED_KEY_PREFIX + "compensate:" + event.getEventId().toString();
                redisTemplate.opsForValue().set(redisKey, "true", PROCESSED_TTL);
                log.info("Compensate event Redis'e kaydedildi: key={}, TTL=24h", redisKey);
            }
        }
    }

    @DltHandler
    public void handlePaymentFailedDlt(PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: PaymentFailedEvent işlenemedi! orderId={}. " +
                    "Stok iadesi (Saga compensate) yapılamadı — manuel düzeltme gerekiyor!", topic, event.getOrderId());
        }
    }
}
