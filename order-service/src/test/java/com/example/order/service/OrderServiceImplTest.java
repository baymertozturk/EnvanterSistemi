package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.kafka.event.OrderCreatedEvent;
import com.example.order.kafka.producer.OrderEventProducer;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl için birim testler.
 *
 * MOCK vs ENTEGRASYON TESTİ FARKI:
 * ─────────────────────────────────
 * Bu dosyadaki testler MOCK testleri:
 * - OrderRepository ve OrderEventProducer Mockito ile sahte nesnelere dönüştürülür.
 * - Gerçek bir veritabanı veya Kafka bağlantısı YOK.
 * - Avantaj: Çok hızlı (~ms), harici bağımlılık gerektirmez.
 * - Dezavantaj: Gerçek servis davranışını (SQL, Kafka serialization) test etmez.
 *
 * Gerçek entegrasyon testi için OrderServiceIntegrationTest.java'ya bakın.
 * Orada Testcontainers ile gerçek PostgreSQL + Kafka Docker container'ı kullanılır.
 *
 * Test Yapısı:
 * - @Nested sınıflar: İlgili testleri gruplayarak okunabilirliği artırır.
 * - @DisplayName: Test raporunda anlaşılır Türkçe açıklamalar.
 * - @ExtendWith(MockitoExtension): Mockito'yu JUnit 5 ile entegre eder.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    // Mock nesneler: Gerçek implementasyon yerine Mockito sahte nesneleri.
    // Bu sayede OrderServiceImpl'i izole ederek sadece kendi mantığını test ederiz.
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    // @InjectMocks: Mockları yukarıdaki sınıfa inject eder (constructor injection).
    @InjectMocks
    private OrderServiceImpl orderService;

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 1: Sipariş Oluşturma
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sipariş Oluşturma Senaryoları")
    class CreateOrderTests {

        @Test
        @DisplayName("Geçerli request ile sipariş PENDING durumunda oluşturulmalı")
        void shouldCreateOrderWithPendingStatus() {
            // Arrange — ne bekliyoruz
            OrderRequest request = OrderRequest.builder()
                    .customerId("customer-001")
                    .productId("prod-abc-123")
                    .quantity(5)
                    .build();

            UUID generatedId = UUID.randomUUID();
            Order savedOrder = Order.builder()
                    .id(generatedId)
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status(OrderStatus.PENDING)
                    .build();

            // Repository mock: save() çağrıldığında savedOrder döndür
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Act — metodu çalıştır
            OrderResponse response = orderService.createOrder(request);

            // Assert — sonuçları kontrol et
            assertNotNull(response, "Response null olmamalı");
            assertEquals(generatedId, response.getId(), "Sipariş ID eşleşmeli");
            assertEquals(OrderStatus.PENDING, response.getStatus(), "İlk durum PENDING olmalı");
            assertEquals("customer-001", response.getCustomerId());
            assertEquals("prod-abc-123", response.getProductId());
            assertEquals(5, response.getQuantity());

            // Repository bir kez çağrıldı mı?
            verify(orderRepository, times(1)).save(any(Order.class));
        }

        @Test
        @DisplayName("Sipariş oluşturulduğunda benzersiz eventId ile OrderCreatedEvent Kafka'ya yayınlanmalı")
        void shouldPublishOrderCreatedEventWithUniqueEventId() {
            // Arrange
            OrderRequest request = OrderRequest.builder()
                    .customerId("customer-002")
                    .productId("prod-xyz-456")
                    .quantity(3)
                    .build();

            UUID orderId = UUID.randomUUID();
            Order savedOrder = Order.builder()
                    .id(orderId)
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status(OrderStatus.PENDING)
                    .build();

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Act
            orderService.createOrder(request);

            // ArgumentCaptor: Producer'a gönderilen event'i yakala
            // Bu sayede event'in içeriğini detaylıca kontrol edebiliyoruz.
            ArgumentCaptor<OrderCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(orderEventProducer, times(1)).sendOrderCreatedEvent(eventCaptor.capture());

            OrderCreatedEvent publishedEvent = eventCaptor.getValue();

            // Event alanlarını doğrula
            assertNotNull(publishedEvent.getEventId(),
                    "Her event için benzersiz UUID üretilmeli (idempotency için)");
            assertEquals(orderId, publishedEvent.getOrderId(),
                    "Event'teki orderId, kaydedilen siparişin ID'si olmalı");
            assertEquals("prod-xyz-456", publishedEvent.getProductId());
            assertEquals(3, publishedEvent.getQuantity());
        }

        @Test
        @DisplayName("İki farklı siparişin eventId'leri farklı olmalı (UUID benzersizliği)")
        void shouldGenerateDifferentEventIdsForEachOrder() {
            // Arrange — iki farklı sipariş
            OrderRequest req1 = OrderRequest.builder()
                    .customerId("c1").productId("p1").quantity(1).build();
            OrderRequest req2 = OrderRequest.builder()
                    .customerId("c2").productId("p2").quantity(2).build();

            Order order1 = Order.builder().id(UUID.randomUUID())
                    .customerId("c1").productId("p1").quantity(1).status(OrderStatus.PENDING).build();
            Order order2 = Order.builder().id(UUID.randomUUID())
                    .customerId("c2").productId("p2").quantity(2).status(OrderStatus.PENDING).build();

            when(orderRepository.save(any(Order.class)))
                    .thenReturn(order1)
                    .thenReturn(order2);

            // Act
            orderService.createOrder(req1);
            orderService.createOrder(req2);

            // Capture her iki event'i
            ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(orderEventProducer, times(2)).sendOrderCreatedEvent(captor.capture());

            List<OrderCreatedEvent> events = captor.getAllValues();
            assertNotEquals(events.get(0).getEventId(), events.get(1).getEventId(),
                    "Her sipariş için benzersiz eventId üretilmeli");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 2: Sipariş Bulunamadı
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sipariş Sorgulama Senaryoları")
    class GetOrderTests {

        @Test
        @DisplayName("Var olmayan orderId ile getOrderById → OrderNotFoundException fırlatmalı")
        void shouldThrowOrderNotFoundExceptionWhenOrderDoesNotExist() {
            // Arrange — repo var olmayan ID için empty döndürür
            UUID nonExistentId = UUID.randomUUID();
            when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert — Exception fırlatıldığını doğrula
            // assertThrows: Beklenen exception türünü ve mesajını kontrol eder
            OrderNotFoundException exception = assertThrows(
                    OrderNotFoundException.class,
                    () -> orderService.getOrderById(nonExistentId),
                    "Var olmayan sipariş için OrderNotFoundException fırlatılmalı"
            );

            assertTrue(exception.getMessage().contains(nonExistentId.toString()),
                    "Hata mesajı orderId içermeli");

            // Repo bir kez sorgulandı mı?
            verify(orderRepository, times(1)).findById(nonExistentId);
        }

        @Test
        @DisplayName("Var olan orderId ile getOrderById → doğru siparişi döndürmeli")
        void shouldReturnOrderWhenExists() {
            // Arrange
            UUID orderId = UUID.randomUUID();
            Order order = Order.builder()
                    .id(orderId)
                    .customerId("cust-123")
                    .productId("prod-456")
                    .quantity(7)
                    .status(OrderStatus.STOCK_RESERVED)
                    .build();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // Act
            OrderResponse response = orderService.getOrderById(orderId);

            // Assert
            assertNotNull(response);
            assertEquals(orderId, response.getId());
            assertEquals(OrderStatus.STOCK_RESERVED, response.getStatus());
            assertEquals(7, response.getQuantity());
        }

        @Test
        @DisplayName("getAllOrders → tüm siparişleri response listesi olarak döndürmeli")
        void shouldReturnAllOrders() {
            // Arrange — repo 3 sipariş döndürüyor
            List<Order> orders = List.of(
                    Order.builder().id(UUID.randomUUID()).customerId("c1").productId("p1")
                            .quantity(1).status(OrderStatus.PENDING).build(),
                    Order.builder().id(UUID.randomUUID()).customerId("c2").productId("p2")
                            .quantity(2).status(OrderStatus.PAYMENT_COMPLETED).build(),
                    Order.builder().id(UUID.randomUUID()).customerId("c3").productId("p3")
                            .quantity(3).status(OrderStatus.FAILED).build()
            );
            when(orderRepository.findAll()).thenReturn(orders);

            // Act
            List<OrderResponse> responses = orderService.getAllOrders();

            // Assert
            assertEquals(3, responses.size(), "Tüm siparişler döndürülmeli");
            verify(orderRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("getOrdersByCustomerId → filtrelenmiş sipariş listesi döndürmeli")
        void shouldReturnOrdersFilteredByCustomerId() {
            // Arrange
            String customerId = "customer-999";
            List<Order> customerOrders = List.of(
                    Order.builder().id(UUID.randomUUID()).customerId(customerId)
                            .productId("p1").quantity(1).status(OrderStatus.PENDING).build()
            );
            when(orderRepository.findByCustomerId(customerId)).thenReturn(customerOrders);

            // Act
            List<OrderResponse> responses = orderService.getOrdersByCustomerId(customerId);

            // Assert
            assertEquals(1, responses.size());
            assertEquals(customerId, responses.get(0).getCustomerId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENARYO 3: Hatalı/Sınır Durumları
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Hatalı İstek Senaryoları")
    class ErrorScenarioTests {

        @Test
        @DisplayName("Geçersiz miktar (quantity = 0) ile createOrder çağrıldığında IllegalArgumentException fırlatmalı")
        void shouldThrowIllegalArgumentExceptionWhenQuantityIsZero() {
            OrderRequest request = OrderRequest.builder()
                    .customerId("cust-1")
                    .productId("prod-1")
                    .quantity(0)
                    .build();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> orderService.createOrder(request),
                    "quantity <= 0 durumunda IllegalArgumentException fırlatılmalı"
            );

            assertTrue(exception.getMessage().contains("Miktar en az 1 olmalıdır"));
            verify(orderRepository, never()).save(any());
            verify(orderEventProducer, never()).sendOrderCreatedEvent(any());
        }

        @Test
        @DisplayName("Negatif miktar (quantity < 0) ile createOrder çağrıldığında IllegalArgumentException fırlatmalı")
        void shouldThrowIllegalArgumentExceptionWhenQuantityIsNegative() {
            OrderRequest request = OrderRequest.builder()
                    .customerId("cust-1")
                    .productId("prod-1")
                    .quantity(-5)
                    .build();

            assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
            verify(orderRepository, never()).save(any());
            verify(orderEventProducer, never()).sendOrderCreatedEvent(any());
        }

        @Test
        @DisplayName("Miktar null olduğunda createOrder çağrıldığında IllegalArgumentException fırlatmalı")
        void shouldThrowIllegalArgumentExceptionWhenQuantityIsNull() {
            OrderRequest request = OrderRequest.builder()
                    .customerId("cust-1")
                    .productId("prod-1")
                    .quantity(null)
                    .build();

            assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
            verify(orderRepository, never()).save(any());
            verify(orderEventProducer, never()).sendOrderCreatedEvent(any());
        }

        @Test
        @DisplayName("Repository exception fırlatırsa servis de exception üretmeli (propagation)")
        void shouldPropagateRepositoryException() {
            // Arrange — repo hata fırlatıyor (örn. DB bağlantısı koptu)
            OrderRequest request = OrderRequest.builder()
                    .customerId("c1").productId("p1").quantity(1).build();

            when(orderRepository.save(any(Order.class)))
                    .thenThrow(new RuntimeException("DB bağlantısı kesildi"));

            // Act & Assert — exception propagate edilmeli
            assertThrows(RuntimeException.class,
                    () -> orderService.createOrder(request),
                    "Repository exception'ı servis katmanından geçmeli");

            // Kafka'ya event gönderilmemeli — DB hatası oldu
            verify(orderEventProducer, never()).sendOrderCreatedEvent(any());
        }
    }
}
