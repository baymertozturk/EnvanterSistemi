package com.example.inventory.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * Stok başarıyla rezerve edildiğinde yayınlanan event.
 * Topic: "stock-reserved"
 * Partition key: orderId
 *
 * productId ve quantity alanları, ödeme başarısız olduğunda
 * payment-service'in PaymentFailedEvent'e bu bilgileri eklemesi için
 * gereklidir. Bu sayede inventory-service gerçek stok iadesini yapabilir
 * (Saga compensating transaction).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedEvent {

    private UUID orderId;

    /** Hangi ürünün stoğu rezerve edildi — compensate için gerekli. */
    private String productId;

    /** Kaç adet rezerve edildi — compensate için gerekli. */
    private int quantity;
}
