package com.narturebelle.payments.payment;

import com.narturebelle.payments.model.WebhookEventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Internal only — never serialized directly to a client. A parsed, provider-agnostic webhook event. */
public record WebhookEventPayload(
        String eventId,
        WebhookEventType type,
        Long paymentId,
        String providerReference,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
}
