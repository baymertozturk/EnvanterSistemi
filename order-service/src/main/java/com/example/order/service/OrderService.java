package com.example.order.service;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    OrderResponse getOrderById(UUID id);

    List<OrderResponse> getAllOrders();

    List<OrderResponse> getOrdersByCustomerId(String customerId);
}
