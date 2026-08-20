package com.example.inventory.service;

import com.example.inventory.entity.Product;
import com.example.inventory.kafka.event.OrderCreatedEvent;
import com.example.inventory.kafka.event.PaymentFailedEvent;
import com.example.inventory.kafka.event.StockRejectedEvent;
import com.example.inventory.kafka.event.StockReservedEvent;
import com.example.inventory.kafka.producer.InventoryEventProducer;
import com.example.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * StockReservationServiceImpl için birim testler.
 *
 * TEST EDİLEN MANTIK:
 * 1. Yeterli stok → StockReservedEvent yayınla, stok sayıları güncelle
 * 2. Yetersiz stok → StockRejectedEvent yayınla, stok sayıları değişmemeli
 * 3. Ürün bulunamadı → StockRejectedEvent yayınla
 * 4. Geçersiz productId formatı → StockRejectedEvent yayınla
 * 5. Saga Compensate → availableStock geri artmalı, reservedStock azalmalı
 */
@ExtendWith(MockitoExtension.class)
class StockReservationServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryEventProducer eventProducer;

    @InjectMocks
    private StockReservationServiceImpl stockReservationService;

    // Her testte kullanılacak ortak veriler
    private UUID productId;
    private UUID orderId;
    private String productIdStr;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        productIdStr = productId.toString();
    }

    /**
     * Test için Product nesnesi oluştur.
     * @param available mevcut satılabilir stok
     * @param reserved rezerve edilmiş stok
     */
    private Product buildProduct(int available, int reserved) {
        return Product.builder()
                .id(productId)
                .name("Test Ürünü")
                .availableStock(available)
                .reservedStock(reserved)
                .build();
    }

    /**
     * Test için OrderCreatedEvent oluştur.
     */
    private OrderCreatedEvent buildEvent(int quantity) {
        return OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(orderId)
                .productId(productIdStr)
                .quantity(quantity)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 1: Yeterli Stok
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Yeterli Stok Senaryoları")
    class SufficientStockTests {

        @Test
        @DisplayName("Yeterli stok varken: availableStock azalmalı, reservedStock artmalı")
        void shouldDecreaseAvailableAndIncreaseReservedStock() {
            // Arrange — 50 mevcut stok, 10 rezerve, 5 adet isteniyor
            Product product = buildProduct(50, 10);
            OrderCreatedEvent event = buildEvent(5);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenReturn(product);

            // Act
            stockReservationService.reserveStock(event);

            // Assert — stok sayıları doğru mu?
            assertEquals(45, product.getAvailableStock(),
                    "Rezervasyon sonrası availableStock 50-5=45 olmalı");
            assertEquals(15, product.getReservedStock(),
                    "Rezervasyon sonrası reservedStock 10+5=15 olmalı");

            // Repo kaydetti mi?
            verify(productRepository, times(1)).save(product);
        }

        @Test
        @DisplayName("Yeterli stok varken: StockReservedEvent Kafka'ya yayınlanmalı")
        void shouldPublishStockReservedEvent() {
            // Arrange
            Product product = buildProduct(100, 0);
            OrderCreatedEvent event = buildEvent(10);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            // Act
            stockReservationService.reserveStock(event);

            // Event yakalandı mı?
            ArgumentCaptor<StockReservedEvent> captor =
                    ArgumentCaptor.forClass(StockReservedEvent.class);
            verify(eventProducer, times(1)).sendStockReservedEvent(captor.capture());

            StockReservedEvent publishedEvent = captor.getValue();
            assertEquals(orderId, publishedEvent.getOrderId(),
                    "Event'teki orderId siparişle eşleşmeli");
            assertEquals(productIdStr, publishedEvent.getProductId(),
                    "Event'te productId taşınmalı (Saga compensate için)");
            assertEquals(10, publishedEvent.getQuantity(),
                    "Event'te quantity taşınmalı (Saga compensate için)");
        }

        @Test
        @DisplayName("Stok tam yeterliyken (stok == quantity): rezervasyon başarılı olmalı")
        void shouldSucceedWhenStockExactlyMatchesQuantity() {
            // Sınır durumu: availableStock == quantity
            Product product = buildProduct(5, 0);
            OrderCreatedEvent event = buildEvent(5);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            // Act
            stockReservationService.reserveStock(event);

            // Assert
            assertEquals(0, product.getAvailableStock(), "Stok tam tükenmeli");
            assertEquals(5, product.getReservedStock());
            verify(eventProducer, times(1)).sendStockReservedEvent(any());
            verify(eventProducer, never()).sendStockRejectedEvent(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 2: Yetersiz Stok
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Yetersiz Stok Senaryoları")
    class InsufficientStockTests {

        @Test
        @DisplayName("Yetersiz stok: StockRejectedEvent yayınlanmalı, stok sayıları değişmemeli")
        void shouldPublishRejectedEventWhenStockInsufficient() {
            // Arrange — 3 mevcut stok, 10 adet isteniyor
            Product product = buildProduct(3, 0);
            OrderCreatedEvent event = buildEvent(10);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            stockReservationService.reserveStock(event);

            // Assert — Red event yayınlanmalı
            ArgumentCaptor<StockRejectedEvent> captor =
                    ArgumentCaptor.forClass(StockRejectedEvent.class);
            verify(eventProducer, times(1)).sendStockRejectedEvent(captor.capture());

            StockRejectedEvent rejectedEvent = captor.getValue();
            assertEquals(orderId, rejectedEvent.getOrderId());
            assertTrue(rejectedEvent.getReason().contains("Yetersiz stok"),
                    "Red sebebi açıklayıcı olmalı");

            // Stok sayıları DEĞİŞMEMELİ
            assertEquals(3, product.getAvailableStock(),
                    "Yetersiz stokta availableStock değişmemeli");
            assertEquals(0, product.getReservedStock(),
                    "Yetersiz stokta reservedStock değişmemeli");

            // Başarı event'i GÖNDERİLMEMELİ
            verify(eventProducer, never()).sendStockReservedEvent(any());
            // Ürün DB'ye kaydedilmemeli
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Stok sıfırken: rezervasyon reddedilmeli")
        void shouldRejectWhenStockIsZero() {
            // Sınır durumu: availableStock == 0
            Product product = buildProduct(0, 20);
            OrderCreatedEvent event = buildEvent(1);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            stockReservationService.reserveStock(event);

            // Assert
            verify(eventProducer, times(1)).sendStockRejectedEvent(any());
            verify(eventProducer, never()).sendStockReservedEvent(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 3: Ürün Bulunamadı
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Ürün Bulunamadı Senaryoları")
    class ProductNotFoundTests {

        @Test
        @DisplayName("Var olmayan productId: StockRejectedEvent yayınlanmalı")
        void shouldRejectWhenProductNotFound() {
            // Arrange — repo boş döndürüyor
            OrderCreatedEvent event = buildEvent(5);
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // Act
            stockReservationService.reserveStock(event);

            // Assert
            ArgumentCaptor<StockRejectedEvent> captor =
                    ArgumentCaptor.forClass(StockRejectedEvent.class);
            verify(eventProducer, times(1)).sendStockRejectedEvent(captor.capture());
            assertTrue(captor.getValue().getReason().contains("Ürün bulunamadı"));

            verify(eventProducer, never()).sendStockReservedEvent(any());
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Geçersiz UUID formatında productId: StockRejectedEvent yayınlanmalı")
        void shouldRejectWhenProductIdIsInvalidUUID() {
            // Arrange — UUID olmayan bir productId
            OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(orderId)
                    .productId("bu-gecersiz-bir-uuid")   // UUID formatına uymuyor
                    .quantity(5)
                    .build();

            // Act
            stockReservationService.reserveStock(event);

            // Assert — formatı parse edemedi, red event yayınlamalı
            verify(eventProducer, times(1)).sendStockRejectedEvent(any());
            verify(eventProducer, never()).sendStockReservedEvent(any());
            // Repository hiç sorgulanmamalı
            verify(productRepository, never()).findById(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 4: Saga Compensating Transaction
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Saga Compensating Transaction Senaryoları")
    class CompensateTests {

        @Test
        @DisplayName("Ödeme başarısız: availableStock artmalı, reservedStock azalmalı")
        void shouldRestoreStockOnCompensate() {
            // Arrange — önceden stok rezerve edilmişti: available=45, reserved=5
            Product product = buildProduct(45, 5);
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(orderId)
                    .productId(productIdStr)
                    .quantity(5)
                    .reason("Ödeme reddedildi")
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            // Act
            stockReservationService.compensateReservation(event);

            // Assert — stok iade edildi
            assertEquals(50, product.getAvailableStock(),
                    "Compensate sonrası availableStock 45+5=50 olmalı");
            assertEquals(0, product.getReservedStock(),
                    "Compensate sonrası reservedStock 5-5=0 olmalı");

            verify(productRepository, times(1)).save(product);
        }

        @Test
        @DisplayName("Compensate: productId null ise işlem yapılmamalı")
        void shouldSkipCompensateWhenProductIdNull() {
            // Arrange — productId yok (eski format event)
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(orderId)
                    .productId(null)  // eksik bilgi
                    .quantity(5)
                    .reason("Ödeme başarısız")
                    .build();

            // Act
            stockReservationService.compensateReservation(event);

            // Assert — hiçbir şey yapılmamalı
            verify(productRepository, never()).findById(any());
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Compensate: reservedStock miktardan azsa negatife düşmemeli")
        void shouldNotAllowNegativeReservedStock() {
            // Sınır durumu: rezerve 3 varken 10 iade etmeye çalışıyoruz
            Product product = buildProduct(40, 3);
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(orderId)
                    .productId(productIdStr)
                    .quantity(10)  // reservedStock'tan fazla
                    .reason("test")
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            // Act
            stockReservationService.compensateReservation(event);

            // Assert — reservedStock 0'ın altına inmemeli (Math.max(0, ...) koruması)
            assertEquals(0, product.getReservedStock(),
                    "reservedStock negatife düşmemeli");
            assertEquals(43, product.getAvailableStock(),
                    "Gerçekte iade edilen miktar kadar artmalı (3 adet)");
        }
    }
}
