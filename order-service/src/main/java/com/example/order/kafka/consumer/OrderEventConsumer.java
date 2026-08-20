package com.example.order.kafka.consumer;

import com.example.order.config.FailoverTestConfig;
import com.example.order.entity.OrderStatus;
import com.example.order.kafka.event.PaymentCompletedEvent;
import com.example.order.kafka.event.PaymentFailedEvent;
import com.example.order.kafka.event.StockRejectedEvent;
import com.example.order.kafka.event.StockReservedEvent;
import com.example.order.repository.OrderRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Diğer servislerden gelen event'leri dinleyen consumer.
 * Her işlemde MDC'ye orderId eklenerek JSON loglarda ve distributed tracing'de izlenebilir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final FailoverTestConfig failoverTestConfig;

    // ─────────────────────────────────────────────────────────────
    // stock-reserved
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "stock-reserved", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleStockReserved(StockReservedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info("StockReservedEvent alındı: orderId={}", event.getOrderId());
            failoverTestConfig.throwIfFailing(event.getOrderId(), "stock-reserved");

            orderRepository.findById(event.getOrderId()).ifPresentOrElse(
                    order -> {
                        order.setStatus(OrderStatus.STOCK_RESERVED);
                        orderRepository.save(order);
                        log.info("Sipariş durumu güncellendi: orderId={}, yeniDurum=STOCK_RESERVED",
                                event.getOrderId());
                    },
                    () -> log.warn("Sipariş bulunamadı: orderId={}", event.getOrderId())
            );
        }
    }

    @DltHandler
    public void handleStockReservedDlt(StockReservedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: StockReservedEvent işlenemedi! orderId={}. " +
                    "Sipariş durumu güncellenemedi — manuel inceleme gerekiyor.", topic, event.getOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // stock-rejected
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "stock-rejected", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleStockRejected(StockRejectedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info("StockRejectedEvent alındı: orderId={}, reason={}", event.getOrderId(), event.getReason());
            failoverTestConfig.throwIfFailing(event.getOrderId(), "stock-rejected");

            orderRepository.findById(event.getOrderId()).ifPresentOrElse(
                    order -> {
                        order.setStatus(OrderStatus.FAILED);
                        orderRepository.save(order);
                        log.info("Sipariş durumu güncellendi: orderId={}, yeniDurum=FAILED, sebep={}",
                                event.getOrderId(), event.getReason());
                    },
                    () -> log.warn("Sipariş bulunamadı: orderId={}", event.getOrderId())
            );
        }
    }

    @DltHandler
    public void handleStockRejectedDlt(StockRejectedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: StockRejectedEvent işlenemedi! orderId={}.", topic, event.getOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // payment-completed
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "payment-completed", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info("PaymentCompletedEvent alındı: orderId={}, eventId={}", event.getOrderId(), event.getEventId());
            failoverTestConfig.throwIfFailing(event.getOrderId(), "payment-completed");

            orderRepository.findById(event.getOrderId()).ifPresentOrElse(
                    order -> {
                        order.setStatus(OrderStatus.PAYMENT_COMPLETED);
                        orderRepository.save(order);
                        log.info("Sipariş durumu güncellendi: orderId={}, yeniDurum=PAYMENT_COMPLETED",
                                event.getOrderId());
                    },
                    () -> log.warn("Sipariş bulunamadı: orderId={}", event.getOrderId())
            );
        }
    }

    @DltHandler
    public void handlePaymentCompletedDlt(PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: PaymentCompletedEvent işlenemedi! orderId={}.", topic, event.getOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // payment-failed
    // ─────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "payment-failed", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.info("PaymentFailedEvent alındı: orderId={}, eventId={}, reason={}",
                    event.getOrderId(), event.getEventId(), event.getReason());
            failoverTestConfig.throwIfFailing(event.getOrderId(), "payment-failed");

            orderRepository.findById(event.getOrderId()).ifPresentOrElse(
                    order -> {
                        order.setStatus(OrderStatus.FAILED);
                        orderRepository.save(order);
                        log.info("Sipariş durumu güncellendi: orderId={}, yeniDurum=FAILED (ödeme başarısız)",
                                event.getOrderId());
                    },
                    () -> log.warn("Sipariş bulunamadı: orderId={}", event.getOrderId())
            );
        }
    }

    @DltHandler
    public void handlePaymentFailedDlt(PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(event.getOrderId()))) {
            log.error("💀 DLT [{}]: PaymentFailedEvent işlenemedi! orderId={}.", topic, event.getOrderId());
        }
    }
}
