package com.example.inventory.service;

import com.example.inventory.kafka.event.OrderCreatedEvent;
import com.example.inventory.kafka.event.PaymentFailedEvent;

/**
 * Stok rezervasyon ve telafi işlemleri servis arayüzü.
 */
public interface StockReservationService {

    /**
     * Stok rezervasyonu — order-created event geldiğinde çağrılır.
     */
    void reserveStock(OrderCreatedEvent event);

    /**
     * Stok iadesi — payment-failed event geldiğinde çağrılır.
     *
     * Bu bir COMPENSATING TRANSACTION'dır (Saga telafi mekanizması).
     * @param event payment-service'ten gelen ödeme başarısızlık event'i
     */
    void compensateReservation(PaymentFailedEvent event);
}
