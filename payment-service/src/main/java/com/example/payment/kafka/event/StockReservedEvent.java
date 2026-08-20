package com.example.payment.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * inventory-service'ten gelen stok rezervasyon event'i.
 * Topic: "stock-reserved"
 *
 * Mikroservis bağımsızlığı: Bu sınıf inventory-service'teki sınıfın
 * bilinçli kopyasıdır. Shared library kullanmıyoruz.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedEvent {

    private UUID orderId;

    /** Hangi ürünün stoğu rezerve edildi — PaymentFailedEvent'e taşınacak. */
    private String productId;

    /** Kaç adet rezerve edildi — PaymentFailedEvent'e taşınacak. */
    private int quantity;
}
