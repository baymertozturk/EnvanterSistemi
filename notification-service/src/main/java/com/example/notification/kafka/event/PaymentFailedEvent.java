package com.example.notification.kafka.event;

import lombok.*;
import java.util.UUID;

/** payment-service'ten gelen ödeme başarısızlık event'i. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentFailedEvent {
    private UUID eventId;
    private UUID orderId;
    private String reason;
    private String productId;
    private int quantity;
}
