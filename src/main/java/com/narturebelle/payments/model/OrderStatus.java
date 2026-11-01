package com.narturebelle.payments.model;

/** DISPUTED exists for shape-completeness only — nothing in Phase 2 triggers it (no real gateway sends dispute webhooks until Phase 7). */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    DISPUTED
}
