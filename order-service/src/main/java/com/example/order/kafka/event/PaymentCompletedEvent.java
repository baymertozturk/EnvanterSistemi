package com.example.order.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * payment-service'ten gelen ödeme tamamlanma event'i.
 * Topic: "payment-completed"
 *
 * Mikroservis bağımsızlığı: payment-service'teki sınıfın bilinçli kopyası.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {

    private UUID eventId;
    private UUID orderId;
}
