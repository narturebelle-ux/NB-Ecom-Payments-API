package com.narturebelle.payments.scheduling;

import com.narturebelle.payments.model.Payment;
import com.narturebelle.payments.model.PaymentReconciliationFlag;
import com.narturebelle.payments.model.PaymentStatus;
import com.narturebelle.payments.repository.PaymentReconciliationFlagRepository;
import com.narturebelle.payments.repository.PaymentRepository;
import com.narturebelle.payments.security.AuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Structural only -- there is no real payment gateway to actually query for reconciliation yet
 * (MockPaymentProvider has no "check status" API, and Phase 7's real gateway doesn't exist). What
 * this CAN honestly do: find payments stuck in CREATED (never resolved to SUCCEEDED/FAILED by a
 * webhook) past a threshold, and flag them once for a human to look at, via an audit event. Each
 * payment is flagged at most once (PaymentReconciliationFlag), not on every job run.
 */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class PaymentReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentReconciliationFlagRepository paymentReconciliationFlagRepository;
    private final AuditService auditService;

    public PaymentReconciliationJob(PaymentRepository paymentRepository,
                                     PaymentReconciliationFlagRepository paymentReconciliationFlagRepository,
                                     AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.paymentReconciliationFlagRepository = paymentReconciliationFlagRepository;
        this.auditService = auditService;
    }

    @Scheduled(fixedRate = 1_800_000) // every 30 minutes
    public void flagStuckPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Payment> stuck = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.CREATED, threshold);
        for (Payment payment : stuck) {
            if (paymentReconciliationFlagRepository.existsByPaymentId(payment.getId())) {
                continue;
            }
            PaymentReconciliationFlag flag = new PaymentReconciliationFlag();
            flag.setPaymentId(payment.getId());
            paymentReconciliationFlagRepository.save(flag);
            auditService.record("PAYMENT_RECONCILIATION_STUCK", "PAYMENT", String.valueOf(payment.getId()),
                    "Payment has been CREATED for over 30 minutes with no webhook resolution",
                    Map.of("orderId", payment.getOrderId()));
        }
    }
}
