package com.example.inventory.service;

import com.example.inventory.entity.Product;
import com.example.inventory.kafka.event.OrderCreatedEvent;
import com.example.inventory.kafka.event.PaymentFailedEvent;
import com.example.inventory.kafka.event.StockRejectedEvent;
import com.example.inventory.kafka.event.StockReservedEvent;
import com.example.inventory.kafka.producer.InventoryEventProducer;
import com.example.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Stok rezervasyon iş mantığı implementasyonu.
 *
 * Bu sınıf Saga choreography pattern'inin inventory-service ayağıdır.
 *
 * NORMAL AKIŞ:
 * 1. order-created event'i → reserveStock() → stock-reserved yayınla
 *
 * TELAFİ AKIŞI (Compensating Transaction):
 * 2. payment-failed event'i → compensateReservation() → stok iade et
 *
 * Saga Choreography vs Orchestration:
 * - Choreography: Her servis kendi kararını verir, event zinciri oluşturur (bu proje)
 * - Orchestration: Merkezi bir Saga Manager tüm adımları yönetir
 *
 * Optimistic locking (@Version) sayesinde eşzamanlı stok güncellemeleri güvenlidir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationServiceImpl implements StockReservationService {

    private final ProductRepository productRepository;
    private final InventoryEventProducer eventProducer;

    @Override
    @Transactional
    public void reserveStock(OrderCreatedEvent event) {
        UUID productId;
        try {
            productId = UUID.fromString(event.getProductId());
        } catch (IllegalArgumentException e) {
            rejectStock(event, "Geçersiz product ID formatı: " + event.getProductId());
            return;
        }

        // 1. Ürünü bul
        Optional<Product> productOpt = productRepository.findById(productId);

        if (productOpt.isEmpty()) {
            rejectStock(event, "Ürün bulunamadı: " + event.getProductId());
            return;
        }

        Product product = productOpt.get();

        // 2. Stok kontrolü
        if (product.getAvailableStock() < event.getQuantity()) {
            rejectStock(event, String.format(
                    "Yetersiz stok: ürün=%s, mevcut=%d, istenen=%d",
                    product.getName(), product.getAvailableStock(), event.getQuantity()));
            return;
        }

        // 3. Stok rezervasyonu: availableStock azalt, reservedStock artır
        product.setAvailableStock(product.getAvailableStock() - event.getQuantity());
        product.setReservedStock(product.getReservedStock() + event.getQuantity());

        // @Version sayesinde eşzamanlı güncellemelerde optimistic lock devreye girer
        productRepository.save(product);

        log.info("Stok rezerve edildi: orderId={}, productId={}, quantity={}, kalanStok={}",
                event.getOrderId(), event.getProductId(),
                event.getQuantity(), product.getAvailableStock());

        // 4. Başarı event'i yayınla (productId ve quantity dahil — compensate için)
        eventProducer.sendStockReservedEvent(
                StockReservedEvent.builder()
                        .orderId(event.getOrderId())
                        .productId(event.getProductId())   // Saga compensate için taşınıyor
                        .quantity(event.getQuantity())     // Saga compensate için taşınıyor
                        .build());
    }

    /**
     * SAGA COMPENSATING TRANSACTION — Ödeme Başarısız Olduğunda Stok İadesi.
     *
     * NEDEN GEREKLİ?
     * ─────────────
     * Saga pattern'inde her adım başarısız olabilir. Başarısız olan adımdan
     * önceki adımların etkilerini GERİ ALMAK için "compensating transaction"
     * kullanılır.
     *
     * Senaryo:
     * 1. inventory-service: stok rezerve etti (availableStock↓, reservedStock↑)
     * 2. payment-service: ödeme BAŞARISIZ oldu
     * 3. Bu metot çağrılır → adım 1'i geri alır (availableStock↑, reservedStock↓)
     *
     * Bu olmadan ne olurdu?
     * Stok asla "reserved" durumundan çıkmazdı. Ürün satılmamış olmasına rağmen
     * stok düşük görünürdü → "ghost reservation" sorunu ve para kaybı.
     *
     * @param event payment-service'ten gelen başarısızlık event'i
     *              (productId ve quantity stock-reserved'dan taşınıyor)
     */
    @Override
    @Transactional
    public void compensateReservation(PaymentFailedEvent event) {
        log.warn("SAGA COMPENSATE başlatılıyor: orderId={}, productId={}, quantity={}, reason={}",
                event.getOrderId(), event.getProductId(), event.getQuantity(), event.getReason());

        if (event.getProductId() == null || event.getProductId().isBlank()) {
            log.error("SAGA COMPENSATE başarısız: productId null/boş. orderId={}", event.getOrderId());
            return;
        }

        UUID productId;
        try {
            productId = UUID.fromString(event.getProductId());
        } catch (IllegalArgumentException e) {
            log.error("SAGA COMPENSATE başarısız: geçersiz productId={}. orderId={}",
                    event.getProductId(), event.getOrderId());
            return;
        }

        productRepository.findById(productId).ifPresentOrElse(
                product -> {
                    // Stoku iade et: reserveStock'un tam tersi işlem
                    //   reserveStock:  availableStock↓, reservedStock↑
                    //   compensate:    availableStock↑, reservedStock↓
                    int quantityToReturn = event.getQuantity();
                    int newReservedStock = Math.max(0, product.getReservedStock() - quantityToReturn);
                    int actualReturned = product.getReservedStock() - newReservedStock;

                    product.setReservedStock(newReservedStock);
                    product.setAvailableStock(product.getAvailableStock() + actualReturned);

                    productRepository.save(product);

                    log.warn("SAGA COMPENSATE tamamlandı: orderId={}, productId={}, " +
                            "iadeEdilen={}, yeniAvailableStock={}, yeniReservedStock={}",
                            event.getOrderId(), event.getProductId(), actualReturned,
                            product.getAvailableStock(), product.getReservedStock());
                },
                () -> log.error("SAGA COMPENSATE başarısız: ürün bulunamadı: productId={}", event.getProductId())
        );
    }

    /**
     * Stok reddi — reason ile birlikte StockRejectedEvent yayınlar.
     */
    private void rejectStock(OrderCreatedEvent event, String reason) {
        log.warn("Stok reddedildi: orderId={}, reason={}", event.getOrderId(), reason);

        eventProducer.sendStockRejectedEvent(
                StockRejectedEvent.builder()
                        .orderId(event.getOrderId())
                        .reason(reason)
                        .build());
    }
}
