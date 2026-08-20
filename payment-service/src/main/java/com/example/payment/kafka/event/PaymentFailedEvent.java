package com.example.payment.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * Ödeme başarısız olduğunda yayınlanan event.
 * Topic: "payment-failed"
 * Partition key: orderId
 *
 * Bu event iki servis tarafından dinlenir:
 * 1. order-service → siparişi FAILED yap
 * 2. inventory-service → Saga compensating transaction (stok iade)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {

    /** İdempotency kontrolü için benzersiz event kimliği. */
    private UUID eventId;

    /** İlgili sipariş ID'si. */
    private UUID orderId;

    /** Ödeme başarısızlık sebebi. */
    private String reason;

    /**
     * Rezerve edilen ürün ID'si.
     * inventory-service'in Saga compensate işlemi için gerekli.
     * stock-reserved event'inden taşınır.
     */
    private String productId;

    /**
     * Rezerve edilen miktar.
     * inventory-service'in Saga compensate işlemi için gerekli.
     * stock-reserved event'inden taşınır.
     */
    private int quantity;
}
