package com.example.order.kafka.producer;

import com.example.order.kafka.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Order event'lerini Kafka'ya yayınlayan producer.
 *
 * Partition key olarak orderId kullanılır:
 * - Aynı siparişe ait tüm event'ler aynı partition'a gider
 * - Bu sayede bir sipariş için event sıralaması (ordering) garanti edilir
 * - Farklı siparişler farklı partition'lara dağılarak paralellik sağlanır
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_ORDER_CREATED = "order-created";

    /**
     * Sipariş oluşturulduğunda "order-created" topic'ine event yayınlar.
     *
     * @param event Sipariş bilgilerini içeren event
     */
    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        // orderId partition key olarak kullanılır
        String partitionKey = event.getOrderId().toString();

        kafkaTemplate.send(TOPIC_ORDER_CREATED, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("OrderCreatedEvent gönderildi: eventId={}, orderId={}, topic={}, partition={}, offset={}",
                                event.getEventId(),
                                event.getOrderId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("OrderCreatedEvent gönderilemedi: eventId={}, orderId={}, hata={}",
                                event.getEventId(), event.getOrderId(), ex.getMessage(), ex);
                    }
                });
    }
}
