package com.example.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ödeme kaydı entity'si.
 *
 * Her Payment kaydı bir Order'a karşılık gelir.
 * orderId ile order-service'teki siparişle ilişkiseldir
 * (foreign key değil — mikroservisler arası direkt bağımlılık yok).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * İlgili sipariş ID'si (order-service'te karşılığı var).
     * Mikroservis mimarisinde FK yok, sadece UUID referansı tutulur.
     */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * Ödeme tutarı. Simülasyon amaçlı sabit değer atanır.
     */
    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * Ödemenin sonucu: SUCCESS veya FAILED.
     * @see PaymentStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /**
     * Kaydın oluşturulma zamanı — otomatik set edilir.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
