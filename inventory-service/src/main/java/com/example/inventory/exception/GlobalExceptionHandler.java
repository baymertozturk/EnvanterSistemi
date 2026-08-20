package com.example.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 — Ürün bulunamadı.
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(
            ProductNotFoundException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Not Found",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 400 — Validasyon hataları (name boş, stok negatif vb.).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation Failed",
                "errors", fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 409 Conflict — Optimistic Locking hatası.
     *
     * <p>Bu hata, iki eşzamanlı istek aynı ürünü güncellemeye çalıştığında oluşur:</p>
     * <ol>
     *   <li>İstek A: Ürünü okur (version=3), stoğu günceller → Başarılı, version=4</li>
     *   <li>İstek B: Ürünü okumuştu (version=3), stoğu günceller → BAŞARISIZ!
     *       Çünkü version artık 4, İstek B'nin okuduğu 3 değil.</li>
     * </ol>
     *
     * <p>İstemci bu 409 hatasını aldığında yapması gereken:</p>
     * <ol>
     *   <li>Ürünü tekrar GET ile okuyun (güncel version alınır)</li>
     *   <li>Güncelleme isteğini yeniden gönderin (retry)</li>
     * </ol>
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.CONFLICT.value(),
                "error", "Conflict — Optimistic Lock",
                "message", "Bu kayıt başka bir işlem tarafından güncellenmiş. "
                        + "Lütfen güncel veriyi okuyup tekrar deneyin."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
