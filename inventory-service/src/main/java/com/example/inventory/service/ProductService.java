package com.example.inventory.service;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.dto.StockUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(UUID id);

    List<ProductResponse> getAllProducts();

    /**
     * Ürün stoğunu günceller.
     * Optimistic locking sayesinde eşzamanlı güncelleme çakışmalarında
     * {@code ObjectOptimisticLockingFailureException} fırlatılır.
     */
    ProductResponse updateStock(UUID id, StockUpdateRequest request);
}
