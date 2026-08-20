package com.example.payment.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * Ödeme başarıyla tamamlandığında yayınlanan event.
 * Topic: "payment-completed"
 * Partition key: orderId
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {

    /** İdempotency kontrolü için benzersiz event kimliği. */
    private UUID eventId;

    /** İlgili sipariş ID'si. */
    private UUID orderId;
}
