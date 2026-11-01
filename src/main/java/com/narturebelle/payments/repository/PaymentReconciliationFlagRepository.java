package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.PaymentReconciliationFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReconciliationFlagRepository extends JpaRepository<PaymentReconciliationFlag, Long> {
    boolean existsByPaymentId(Long paymentId);
}
