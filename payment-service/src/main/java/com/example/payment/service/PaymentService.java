package com.example.payment.service;

import com.example.payment.kafka.event.StockReservedEvent;

/**
 * Ödeme işlemi servis arayüzü.
 */
public interface PaymentService {

    /**
     * Stok rezervasyonu tamamlanan sipariş için ödemeyi işle.
     * Sonuç olarak payment-completed veya payment-failed event'i yayınlanır.
     *
     * @param event inventory-service'ten gelen stok rezervasyon event'i
     */
    void processPayment(StockReservedEvent event);
}
