package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByProviderEventId(String providerEventId);
}
