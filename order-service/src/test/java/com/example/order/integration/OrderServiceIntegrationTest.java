package com.example.order.integration;

import com.example.order.dto.OrderRequest;
import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderService için GERÇEK entegrasyon testi.
 *
 * MOCK vs TESTCONTAINERS FARK TABLOSU:
 * ─────────────────────────────────────────────────────────────────────────────
 * Özellik          │ Mock (OrderServiceImplTest)     │ Testcontainers (Bu sınıf)
 * ─────────────────│─────────────────────────────────│──────────────────────────
 * Hız              │ ~10ms/test                      │ ~10-30sn (ilk çalıştırma)
 * PostgreSQL       │ Mock (sahte Repository)         │ GERÇEK Docker PostgreSQL
 * Kafka            │ Mock (doğrudan inject)          │ GERÇEK Docker Kafka
 * Güvenilirlik     │ Servis mantığını test eder      │ Uçtan uca davranışı test eder
 * Kapsam           │ Birim test (unit)               │ Entegrasyon testi (integration)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * ÖN KOŞUL: Bu test Docker Desktop'ın çalışıyor olmasını gerektirir.
 * Docker yoksa veya erişilemiyorsa testler otomatik SKIPPED olur.
 *
 * @Testcontainers: Sınıf başında Docker container'larını başlatır, sonunda kapatır.
 * @Container: Bu annotation ile işaretlenen field Docker container'ı temsil eder.
 * @SpringBootTest: Tam Spring ApplicationContext'i ayağa kaldırır.
 * DynamicPropertySource: Container'ların portlarını Spring'e dinamik olarak bildirir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceIntegrationTest {

    /**
     * Docker erişilebilirliğini kontrol et.
     * Docker yoksa tüm testleri SKIP et (FAIL değil).
     * Bu sayede CI ortamında Docker yokken bile build başarılı olur.
     */
    @BeforeAll
    static void checkDockerAvailable() {
        Assumptions.assumeTrue(
                isDockerAvailable(),
                "Docker mevcut değil — Testcontainers entegrasyon testleri atlanıyor. " +
                "Docker Desktop'ı başlatıp tekrar çalıştırın."
        );
    }

    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * PostgreSQL Container — gerçek bir PostgreSQL instance'ı ayağa kaldırır.
     * İlk çalıştırmada Docker Hub'dan image çeker (~30sn), sonrasında cache'den gelir.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("orders_db_test")
            .withUsername("test_admin")
            .withPassword("test_pass");

    /**
     * Kafka Container — gerçek bir Apache Kafka başlatır (KRaft modu, ZooKeeper gerektirmez).
     * confluentinc/cp-kafka imajı kullanılır (Testcontainers default).
     */
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    /**
     * Container bilgilerini Spring ApplicationContext'e inject et.
     * Bu sayede application.yml'deki connection string'leri override edilir.
     * Container her çalıştırmada rastgele port alır — statik port çakışması olmaz.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL bağlantısı — container'ın dinamik portunu kullan
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Flyway — migration'ları test DB'sine uygula
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        // Kafka bootstrap-servers — container'ın dinamik portunu kullan
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Redis'i devre dışı bırak (entegrasyon testinde ihtiyacımız yok)
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");  // var olmayan port
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void cleanUp() {
        // Her testten sonra DB temizle — testler birbirini etkilemesin
        orderRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: Sipariş PostgreSQL'e kaydedildi mi?
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[INTEGRATION] Sipariş oluşturulduğunda gerçek PostgreSQL'e kaydedilmeli")
    void shouldPersistOrderToRealPostgreSQL() {
        // Arrange
        OrderRequest request = OrderRequest.builder()
                .customerId("integration-test-customer")
                .productId("b2c3d4e5-f6a7-8901-bcde-f12345678901")
                .quantity(2)
                .build();

        // Act — gerçek servis çağrısı (gerçek DB'ye yazar)
        var response = orderService.createOrder(request);

        // Assert — gerçek DB'den kontrol et
        assertNotNull(response.getId(), "ID gerçek PostgreSQL tarafından üretilmeli");
        assertEquals(OrderStatus.PENDING, response.getStatus());

        // Repository üzerinden direkt DB sorgusu
        var fromDb = orderRepository.findById(response.getId());
        assertTrue(fromDb.isPresent(), "Sipariş gerçek PostgreSQL'de bulunmalı");
        assertEquals("integration-test-customer", fromDb.get().getCustomerId());
        assertEquals(2, fromDb.get().getQuantity());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Event gerçek Kafka'ya düştü mü?
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[INTEGRATION] Sipariş oluşturulduğunda gerçek Kafka'ya OrderCreatedEvent düşmeli")
    void shouldPublishOrderCreatedEventToRealKafka() throws Exception {
        // Arrange — Kafka Consumer: test içinde gerçek Kafka'yı dinleyeceğiz
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        OrderRequest request = OrderRequest.builder()
                .customerId("kafka-test-customer")
                .productId("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .quantity(5)
                .build();

        // Act — siparişi oluştur (bu aynı zamanda Kafka'ya event yayınlar)
        var response = orderService.createOrder(request);
        assertNotNull(response.getId());

        // Gerçek Kafka'dan event'i oku.
        // NOT: 'order-created' topic'i testler arasında paylaşılıyor ve consumer
        // 'earliest' offset'ten okuyor — yani topic'te bu sınıftaki diğer
        // testlerin bıraktığı event'ler de olabilir. Bu yüzden "ilk mesaj" değil,
        // BU siparişe ait event aranır (testler arası sıra bağımsızlığı).
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("order-created"));

            ObjectMapper objectMapper = new ObjectMapper();
            Map<?, ?> matchedEvent = null;
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();

            while (matchedEvent == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> record : records) {
                    String eventJson = record.value();
                    assertNotNull(eventJson, "Event JSON boş olmamalı");

                    Map<?, ?> eventMap = objectMapper.readValue(eventJson, Map.class);
                    if (response.getId().toString().equals(String.valueOf(eventMap.get("orderId")))) {
                        matchedEvent = eventMap;
                        break;
                    }
                }
            }

            // Assert — bu siparişe ait event gerçek Kafka'ya düşmüş olmalı
            assertNotNull(matchedEvent,
                    "Gerçek Kafka'daki 'order-created' topic'inde bu siparişe ait event bulunmalı");
            assertEquals(5, matchedEvent.get("quantity"),
                    "Event'teki quantity doğru olmalı");
            assertNotNull(matchedEvent.get("eventId"),
                    "Event'te benzersiz eventId bulunmalı (idempotency)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: Çoklu sipariş — gerçek DB'de listele
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[INTEGRATION] Birden fazla sipariş oluşturulup tümü DB'den listelenebilmeli")
    void shouldListAllOrdersFromRealDB() {
        // Arrange — 3 sipariş oluştur
        for (int i = 1; i <= 3; i++) {
            orderService.createOrder(OrderRequest.builder()
                    .customerId("bulk-customer-" + i)
                    .productId("prod-" + i)
                    .quantity(i)
                    .build());
        }

        // Act — gerçek DB'den hepsini getir
        List<Order> orders = orderRepository.findAll();

        // Assert
        assertEquals(3, orders.size(), "Tüm siparişler gerçek PostgreSQL'e kaydedilmeli");
        // Tüm siparişler PENDING durumunda başlamalı
        assertTrue(orders.stream().allMatch(o -> o.getStatus() == OrderStatus.PENDING));
    }
}
