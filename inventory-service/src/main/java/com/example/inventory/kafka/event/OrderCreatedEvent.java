package com.example.inventory.kafka.event;

import lombok.*;

import java.util.UUID;

/**
 * order-service tarafından sipariş oluşturulduğunda yayınlanan event.
 * Topic: "order-created"
 *
 * Bu sınıf, order-service'teki OrderCreatedEvent'in bilinçli bir kopyasıdır.
 * Mikroservis bağımsızlığı için ortak bir kütüphane yerine her servis kendi
 * event modelini tutar. Bu yaklaşımın avantajları:
 * - Servisler birbirinden bağımsız deploy edilebilir
 * - Bir servisteki event değişikliği diğer servisi recompile etmeye zorlamaz
 * - Her servis sadece ihtiyacı olan alanları tutabilir
 *
 * eventId: Consumer tarafında Redis ile idempotency kontrolü için kullanılır.
 * Kafka "at-least-once" delivery garanti eder; aynı mesaj tekrar teslim
 * edilirse eventId sayesinde duplike işleme engellenir.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {

    private UUID eventId;
    private UUID orderId;
    private String productId;
    private Integer quantity;
}
