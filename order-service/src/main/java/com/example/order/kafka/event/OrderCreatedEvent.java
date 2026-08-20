package com.example.order.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * Sipariş oluşturulduğunda Kafka'ya yayınlanan event.
 * Topic: "order-created"
 * Partition key: orderId (aynı siparişe ait event'lerin sıralamasını korur)
 *
 * eventId: Her event'e benzersiz bir kimlik atanır. Consumer tarafında
 * Redis ile idempotency kontrolü için kullanılır. Aynı event birden
 * fazla kez teslim edilse bile (Kafka "at-least-once" semantiği),
 * consumer tarafında yalnızca bir kez işlenir.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {

    private UUID eventId;
    private UUID orderId;
    private String productId;
    private Integer quantity;
}
