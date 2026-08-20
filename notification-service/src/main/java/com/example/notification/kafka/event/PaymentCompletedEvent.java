package com.example.notification.kafka.event;

import lombok.*;
import java.util.UUID;

/** payment-service'ten gelen ödeme tamamlanma event'i. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentCompletedEvent {
    private UUID eventId;
    private UUID orderId;
}
