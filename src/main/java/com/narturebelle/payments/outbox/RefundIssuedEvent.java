package com.narturebelle.payments.outbox;

import com.narturebelle.payments.model.OrderStatus;

import java.math.BigDecimal;

/** orderStatus is the order's status after this refund: Care's paid votes count PAID orders only. */
public record RefundIssuedEvent(
        String eventId,
        int schemaVersion,
        Long refundId,
        Long orderId,
        Long userId,
        BigDecimal amount,
        OrderStatus orderStatus
) {
}
