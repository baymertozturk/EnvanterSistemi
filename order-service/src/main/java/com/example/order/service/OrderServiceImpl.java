package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.kafka.event.OrderCreatedEvent;
import com.example.order.kafka.producer.OrderEventProducer;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Miktar en az 1 olmalıdır: " + request.getQuantity());
        }

        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);

        try (var ignored = MDC.putCloseable("orderId", String.valueOf(savedOrder.getId()))) {
            log.info("Sipariş oluşturuldu: id={}, customerId={}, productId={}, quantity={}",
                    savedOrder.getId(), savedOrder.getCustomerId(),
                    savedOrder.getProductId(), savedOrder.getQuantity());

            // Saga başlat: inventory-service stok kontrolü yapması için event yayınla.
            OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(savedOrder.getId())
                    .productId(savedOrder.getProductId())
                    .quantity(savedOrder.getQuantity())
                    .build();

            orderEventProducer.sendOrderCreatedEvent(event);
            log.info("OrderCreatedEvent Kafka'ya gönderildi: orderId={}", savedOrder.getId());
        }

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        try (var ignored = MDC.putCloseable("orderId", String.valueOf(id))) {
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Sipariş bulunamadı: " + id));
            return mapToResponse(order);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ---- Mapping ----

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
