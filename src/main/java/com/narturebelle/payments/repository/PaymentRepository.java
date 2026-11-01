package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.Payment;
import com.narturebelle.payments.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);
}
