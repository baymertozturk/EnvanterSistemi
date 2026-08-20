package com.example.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Ürün entity'si — Envanter yönetimi için temel model.
 *
 * <h2>OPTIMISTIC LOCKING — {@code @Version} nasıl çalışır?</h2>
 *
 * <p>Optimistic locking, "çakışma nadiren olur" varsayımıyla çalışan bir
 * eşzamanlılık (concurrency) kontrol mekanizmasıdır. Pessimistic locking'in
 * aksine veritabanı seviyesinde satır kilidi (row lock) TUTMAZ, bu yüzden
 * performans açısından çok daha verimlidir.</p>
 *
 * <h3>Adım adım akış:</h3>
 * <ol>
 *   <li>Entity veritabanından okunduğunda, mevcut {@code version} değeri de okunur (örn: version=0)</li>
 *   <li>Entity üzerinde değişiklikler yapılır (örn: availableStock = 95)</li>
 *   <li>JPA, {@code save()} çağrıldığında şu SQL'i üretir:
 *       <pre>
 *       UPDATE products
 *       SET available_stock = 95, version = 1     -- version + 1
 *       WHERE id = :id AND version = 0            -- okunan version
 *       </pre>
 *   </li>
 *   <li>Eğer başka bir transaction bu satırı zaten güncellediyse (version artık 1),
 *       WHERE koşulu sağlanmaz → 0 satır etkilenir →
 *       JPA {@code ObjectOptimisticLockingFailureException} fırlatır</li>
 *   <li>Başarılı güncelleme sonrası version otomatik olarak artırılır</li>
 * </ol>
 *
 * <h3>Somut senaryo — Lost Update problemi ve çözümü:</h3>
 * <pre>
 * Zaman  | Transaction A                     | Transaction B
 * -------|-----------------------------------|-----------------------------------
 * T1     | Product okur (version=0, stok=100)|
 * T2     |                                   | Product okur (version=0, stok=100)
 * T3     | stok=95 yap, kaydet               |
 *        | UPDATE ... WHERE version=0        |
 *        | → Başarılı! version artık 1       |
 * T4     |                                   | stok=98 yap, kaydet
 *        |                                   | UPDATE ... WHERE version=0
 *        |                                   | → BAŞARISIZ! version=1, beklenen=0
 *        |                                   | → OptimisticLockException!
 * </pre>
 *
 * <p>Bu mekanizma olmadan Transaction B'nin güncellemesi Transaction A'nın
 * değişikliğini ezerdi (lost update). {@code @Version} bunu engeller.</p>
 *
 * <p><strong>Önemli:</strong> {@code version} alanını manuel olarak değiştirmeyin!
 * JPA tarafından otomatik yönetilir.</p>
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock;

    /**
     * JPA Optimistic Locking version alanı.
     *
     * - Her başarılı UPDATE'te JPA tarafından otomatik artırılır (version + 1).
     * - Eşzamanlı güncellemelerde çakışma tespiti için kullanılır.
     * - Manuel olarak set edilmemelidir!
     *
     * @see jakarta.persistence.Version
     */
    @Version
    private Long version;
}
