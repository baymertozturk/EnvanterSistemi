package com.example.order.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * inventory-service tarafından stok başarıyla rezerve edildiğinde yayınlanan event.
 * Topic: "stock-reserved"
 * order-service bu event'i dinleyip sipariş durumunu STOCK_RESERVED yapar.
 *
 * productId ve quantity eklendi: payment-service'in bu bilgileri
 * PaymentFailedEvent'e taşıması ve inventory-service'in gerçek stok
 * iadesi yapabilmesi için.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedEvent {

    private UUID orderId;
    private String productId;
    private int quantity;
}
