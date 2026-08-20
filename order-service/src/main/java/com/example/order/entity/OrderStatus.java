package com.example.order.entity;

/**
 * Sipariş yaşam döngüsü durumları.
 *
 * PENDING            → Sipariş oluşturuldu, stok kontrolü bekleniyor
 * STOCK_RESERVED     → Stok başarıyla rezerve edildi, ödeme bekleniyor
 * PAYMENT_COMPLETED  → Ödeme başarıyla tamamlandı
 * COMPLETED          → Sipariş tamamlandı (tüm adımlar başarılı)
 * FAILED             → Sipariş başarısız (stok yetersiz veya ödeme hatası)
 */
public enum OrderStatus {
    PENDING,
    STOCK_RESERVED,
    PAYMENT_COMPLETED,
    COMPLETED,
    FAILED
}
