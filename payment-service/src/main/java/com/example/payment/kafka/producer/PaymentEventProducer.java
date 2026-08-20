package com.example.payment.kafka.producer;

import com.example.payment.kafka.event.PaymentCompletedEvent;
import com.example.payment.kafka.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Ödeme event'lerini Kafka'ya yayınlayan producer.
 * Partition key olarak orderId kullanılır (her sipariş aynı partition'da işlenir).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_PAYMENT_COMPLETED = "payment-completed";
    private static final String TOPIC_PAYMENT_FAILED    = "payment-failed";

    /**
     * Ödeme başarılıydı — order-service'e bildir.
     */
    public void sendPaymentCompletedEvent(PaymentCompletedEvent event) {
        String partitionKey = event.getOrderId().toString();

        kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("PaymentCompletedEvent gönderildi: orderId={}, eventId={}, partition={}, offset={}",
                                event.getOrderId(), event.getEventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("PaymentCompletedEvent gönderilemedi: orderId={}", event.getOrderId(), ex);
                    }
                });
    }

    /**
     * Ödeme başarısız — order-service ve inventory-service'e bildir.
     * Bu event iki farklı consumer group tarafından dinlenir (fan-out pattern).
     */
    public void sendPaymentFailedEvent(PaymentFailedEvent event) {
        String partitionKey = event.getOrderId().toString();

        kafkaTemplate.send(TOPIC_PAYMENT_FAILED, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("PaymentFailedEvent gönderildi: orderId={}, eventId={}, reason={}, partition={}, offset={}",
                                event.getOrderId(), event.getEventId(), event.getReason(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("PaymentFailedEvent gönderilemedi: orderId={}", event.getOrderId(), ex);
                    }
                });
    }
}
