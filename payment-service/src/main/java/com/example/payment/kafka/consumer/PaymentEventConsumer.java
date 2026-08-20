package com.example.payment.kafka.consumer;

import com.example.payment.config.FailoverTestConfig;
import com.example.payment.kafka.event.StockReservedEvent;
import com.example.payment.service.PaymentService;
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
 * inventory-service'ten gelen stok event'lerini dinleyen consumer.
 * Her işlemde MDC'ye orderId eklenerek JSON loglarda ve distributed tracing'de izlenebilir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final RedisTemplate<String, String> redisTemplate;
    private final FailoverTestConfig failoverTestConfig;

    private static final String PROCESSED_KEY_PREFIX = "payment-processed:";
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "stock-reserved", groupId = "${spring.kafka.consumer.group-id}")
    public void handleStockReserved(StockReservedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info("StockReservedEvent alındı: orderId={}", event.getOrderId());

            // Test flag kontrolü — kasıtlı hata tetikle
            failoverTestConfig.throwIfFailing(event.getOrderId(), "stock-reserved");

            // İdempotency kontrolü (orderId bazlı — bir sipariş için tek ödeme)
            String redisKey = PROCESSED_KEY_PREFIX + event.getOrderId().toString();
            Boolean alreadyProcessed = redisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(alreadyProcessed)) {
                log.warn("Duplicate payment event ignored: orderId={}.", event.getOrderId());
                return;
            }

            paymentService.processPayment(event);

            redisTemplate.opsForValue().set(redisKey, "true", PROCESSED_TTL);
            log.info("Ödeme işlemi Redis'e kaydedildi: key={}, TTL=24h", redisKey);
        }
    }

    @DltHandler
    public void handleStockReservedDlt(StockReservedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: StockReservedEvent işlenemedi! orderId={}. " +
                    "Ödeme işlemi yapılamadı — manuel inceleme gerekiyor!", topic, event.getOrderId());
        }
    }
}
