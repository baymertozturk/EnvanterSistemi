package com.example.payment.repository;

import com.example.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Payment JPA repository.
 * Spring Data JPA implementation'ı runtime'da otomatik üretir.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
