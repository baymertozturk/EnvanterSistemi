package com.example.inventory.kafka.producer;

import com.example.inventory.kafka.event.StockRejectedEvent;
import com.example.inventory.kafka.event.StockReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Stok event'lerini Kafka'ya yayınlayan producer.
 * Partition key olarak orderId kullanılır (event sıralaması için).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_STOCK_RESERVED = "stock-reserved";
    private static final String TOPIC_STOCK_REJECTED = "stock-rejected";

    /**
     * Stok başarıyla rezerve edildi — order-service'e bildir.
     */
    public void sendStockReservedEvent(StockReservedEvent event) {
        String partitionKey = event.getOrderId().toString();

        kafkaTemplate.send(TOPIC_STOCK_RESERVED, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("StockReservedEvent gönderildi: orderId={}, partition={}, offset={}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("StockReservedEvent gönderilemedi: orderId={}",
                                event.getOrderId(), ex);
                    }
                });
    }

    /**
     * Stok yetersiz — order-service'e bildir.
     */
    public void sendStockRejectedEvent(StockRejectedEvent event) {
        String partitionKey = event.getOrderId().toString();

        kafkaTemplate.send(TOPIC_STOCK_REJECTED, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("StockRejectedEvent gönderildi: orderId={}, reason={}, partition={}, offset={}",
                                event.getOrderId(), event.getReason(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("StockRejectedEvent gönderilemedi: orderId={}",
                                event.getOrderId(), ex);
                    }
                });
    }
}
