package com.narturebelle.payments.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** The idempotency ledger: a unique providerEventId stops the same webhook delivery being processed twice. */
@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, unique = true)
    private String providerEventId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = LocalDateTime.now();
        }
    }

    public PaymentWebhookEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
