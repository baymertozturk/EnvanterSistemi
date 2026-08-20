package com.example.notification.kafka.event;

import lombok.*;
import java.util.UUID;

/** inventory-service'ten gelen stok reddi event'i. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockRejectedEvent {
    private UUID orderId;
    private String reason;
}
