package com.narturebelle.payments.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Dedupe marker so PaymentReconciliationJob flags a stuck payment once, not every run. */
@Entity
@Table(name = "payment_reconciliation_flags")
public class PaymentReconciliationFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long paymentId;

    @Column(nullable = false)
    private LocalDateTime flaggedAt;

    @PrePersist
    protected void onCreate() {
        if (this.flaggedAt == null) {
            this.flaggedAt = LocalDateTime.now();
        }
    }

    public PaymentReconciliationFlag() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public LocalDateTime getFlaggedAt() { return flaggedAt; }
    public void setFlaggedAt(LocalDateTime flaggedAt) { this.flaggedAt = flaggedAt; }
}
