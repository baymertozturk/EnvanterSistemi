package com.example.payment.entity;

/**
 * Ödeme sonuç durumları.
 *
 * SUCCESS → Ödeme başarıyla tamamlandı. Sıradaki adım sipariş COMPLETED.
 * FAILED  → Ödeme başarısız. Sıradaki adım stok iadesi (Saga compensate).
 */
public enum PaymentStatus {
    SUCCESS,
    FAILED
}
