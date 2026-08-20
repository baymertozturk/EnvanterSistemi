package com.example.inventory.kafka.consumer;

import com.example.inventory.kafka.event.OrderCreatedEvent;
import com.example.inventory.config.FailoverTestConfig;
import com.example.inventory.service.StockReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryEventConsumerTest {

    @Mock
    private StockReservationService stockReservationService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FailoverTestConfig failoverTestConfig;

    private InventoryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InventoryEventConsumer(stockReservationService, redisTemplate, failoverTestConfig);
    }

    @Test
    @DisplayName("İlk gelen event başarıyla işlenmeli ve Redis'e 24 saat TTL ile kaydedilmeli")
    void shouldProcessEventWhenNotProcessedBefore() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String productId = UUID.randomUUID().toString();
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .productId(productId)
                .quantity(2)
                .build();

        String redisKey = "processed:" + eventId;
        when(redisTemplate.hasKey(redisKey)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        consumer.handleOrderCreated(event);

        // Assert
        verify(stockReservationService, times(1)).reserveStock(event);
        verify(valueOperations, times(1)).set(eq(redisKey), eq("true"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Aynı eventId ikinci kez geldiğinde işlenmemeli (Duplicate event ignored)")
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String productId = UUID.randomUUID().toString();
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .productId(productId)
                .quantity(2)
                .build();

        String redisKey = "processed:" + eventId;
        when(redisTemplate.hasKey(redisKey)).thenReturn(true);

        // Act
        consumer.handleOrderCreated(event);

        // Assert: Stok rezervasyonu HİÇ ÇAĞRILMAMALI
        verify(stockReservationService, never()).reserveStock(any());
        // Redis'e tekrar yazılmamalı
        verify(redisTemplate, never()).opsForValue();
    }
}
