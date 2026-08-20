package com.example.inventory.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * Stok yetersiz olduğunda yayınlanan event.
 * Topic: "stock-rejected"
 * Partition key: orderId
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
