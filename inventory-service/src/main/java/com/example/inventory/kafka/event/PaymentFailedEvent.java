package com.example.inventory.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * payment-service'ten gelen ödeme başarısızlık event'i.
 * Topic: "payment-failed"
 *
 * Mikroservis bağımsızlığı: payment-service'teki sınıfın bilinçli kopyası.
 * inventory-service bu event'i Saga compensating transaction için dinler.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {

    private UUID eventId;
    private UUID orderId;
    private String reason;
    private String productId;
    private int quantity;
}
