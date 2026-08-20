package com.example.order.controller;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /orders — Yeni sipariş oluşturur. Durum otomatik olarak PENDING atanır.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /orders/{id} — Belirli bir siparişi UUID ile getirir.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /orders           — Tüm siparişleri listeler.
     * GET /orders?customerId=... — Müşteriye göre filtreler.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(required = false) String customerId) {
        List<OrderResponse> responses;
        if (customerId != null && !customerId.isBlank()) {
            responses = orderService.getOrdersByCustomerId(customerId);
        } else {
            responses = orderService.getAllOrders();
        }
        return ResponseEntity.ok(responses);
    }
}
