package com.example.inventory.service;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.dto.StockUpdateRequest;
import com.example.inventory.entity.Product;
import com.example.inventory.exception.ProductNotFoundException;
import com.example.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .availableStock(request.getAvailableStock())
                .reservedStock(0)
                .build();

        Product saved = productRepository.save(product);
        log.info("Ürün oluşturuldu: id={}, name={}, stock={}",
                saved.getId(), saved.getName(), saved.getAvailableStock());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        Product product = findProductOrThrow(id);
        return mapToResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Ürün stoğunu günceller.
     *
     * <p>Optimistic Locking bu metotta kritik rol oynar:</p>
     *
     * <p>Bu metod {@code @Transactional} olduğu için, transaction commit edilirken
     * JPA şu SQL'i üretir:</p>
     * <pre>
     *   UPDATE products
     *   SET available_stock = :yeniDeger,
     *       version = version + 1
     *   WHERE id = :id
     *     AND version = :okunanVersion
     * </pre>
     *
     * <p>Eğer başka bir thread/transaction bu ürünü bizden önce güncellediyse,
     * {@code version} değişmiştir ve WHERE koşulu sağlanmaz. Bu durumda
     * JPA {@code ObjectOptimisticLockingFailureException} fırlatır ve
     * {@code GlobalExceptionHandler} bunu 409 Conflict olarak döner.</p>
     *
     * <p>İstemci bu hatayı aldığında: ürünü tekrar okumalı (güncel version ile)
     * ve güncelleme isteğini yeniden göndermelidir (retry pattern).</p>
     */
    @Override
    @Transactional
    public ProductResponse updateStock(UUID id, StockUpdateRequest request) {
        Product product = findProductOrThrow(id);

        log.info("Stok güncelleniyor: id={}, name={}, eskiStok={}, yeniStok={}, version={}",
                id, product.getName(),
                product.getAvailableStock(), request.getAvailableStock(),
                product.getVersion());

        product.setAvailableStock(request.getAvailableStock());

        // save() çağrısı transaction commit anında optimistic lock kontrolü yapar.
        // Version uyuşmazlığında ObjectOptimisticLockingFailureException fırlatılır.
        Product updated = productRepository.save(product);

        log.info("Stok güncellendi: id={}, stok={}, yeniVersion={}",
                updated.getId(), updated.getAvailableStock(), updated.getVersion());

        return mapToResponse(updated);
    }

    // ---- Helpers ----

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Ürün bulunamadı: " + id));
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .availableStock(product.getAvailableStock())
                .reservedStock(product.getReservedStock())
                .version(product.getVersion())
                .build();
    }
}
