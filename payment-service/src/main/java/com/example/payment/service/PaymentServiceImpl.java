package com.example.payment.service;

import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.kafka.event.PaymentCompletedEvent;
import com.example.payment.kafka.event.PaymentFailedEvent;
import com.example.payment.kafka.event.StockReservedEvent;
import com.example.payment.kafka.producer.PaymentEventProducer;
import com.example.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

/**
 * Ödeme işlemi iş mantığı.
 *
 * ÖNEMLİ: Bu bir SİMÜLASYON'dur!
 * Gerçek ödeme gateway'i yerine rastgele başarı/başarısızlık üretiyoruz:
 * - %80 ihtimalle SUCCESS → payment-completed
 * - %20 ihtimalle FAILED  → payment-failed → Saga compensate başlar
 *
 * Bu yaklaşım test kolaylığı içindir: sürekli çalıştırarak hem
 * başarılı hem başarısız akışı gözlemleyebilirsin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    /**
     * Random nesnesini field olarak tutuyoruz — her çağrıda new Random() pahalıdır.
     * Gerçek uygulamada SecureRandom kullanılır.
     */
    private final Random random = new Random();

    /**
     * Sabit ödeme tutarı (simülasyon için, gerçekte siparişten gelir).
     * Gerçek sistemde order-service'ten ürün fiyatı alınır.
     */
    private static final BigDecimal SIMULATED_AMOUNT = new BigDecimal("99.99");

    /**
     * %80 başarı eşiği: 0.0–0.8 → SUCCESS, 0.8–1.0 → FAILED
     */
    private static final double SUCCESS_RATE = 0.80;

    @Override
    @Transactional
    public void processPayment(StockReservedEvent event) {
        UUID orderId = event.getOrderId();

        // 1. Ödemeyi simüle et (%80 başarı)
        boolean isSuccess = random.nextDouble() < SUCCESS_RATE;
        PaymentStatus status = isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        log.info("Ödeme işleniyor: orderId={}, simülasyon sonucu={}", orderId, status);

        // 2. Sonucu veritabanına kaydet
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(SIMULATED_AMOUNT)
                .status(status)
                .build();

        paymentRepository.save(payment);

        log.info("Ödeme kaydedildi: paymentId={}, orderId={}, status={}",
                payment.getId(), orderId, status);

        // 3. Sonuca göre uygun Kafka event'ini yayınla
        if (isSuccess) {
            // Başarılı: order-service siparişi PAYMENT_COMPLETED yapacak
            paymentEventProducer.sendPaymentCompletedEvent(
                    PaymentCompletedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(orderId)
                            .build());
        } else {
            // Başarısız: hem order-service hem inventory-service bu event'i dinler.
            // productId ve quantity → inventory-service'in Saga compensate yapabilmesi için
            paymentEventProducer.sendPaymentFailedEvent(
                    PaymentFailedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(orderId)
                            .reason("Ödeme reddedildi: Simüle edilmiş ödeme başarısızlığı (%20 ihtimal)")
                            .productId(event.getProductId())   // stock-reserved'dan gelen bilgi
                            .quantity(event.getQuantity())     // stock-reserved'dan gelen bilgi
                            .build());
        }
    }
}
