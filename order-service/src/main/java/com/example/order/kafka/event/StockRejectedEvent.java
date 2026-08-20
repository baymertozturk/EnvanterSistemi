package com.example.order.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * inventory-service tarafından stok yetersiz olduğunda yayınlanan event.
 * Topic: "stock-rejected"
 * order-service bu event'i dinleyip sipariş durumunu FAILED yapar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRejectedEvent {

    private UUID orderId;
    private String reason;
}
