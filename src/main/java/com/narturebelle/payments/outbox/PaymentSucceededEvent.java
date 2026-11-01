package com.narturebelle.payments.outbox;

import com.narturebelle.payments.model.PlanCode;
import com.narturebelle.payments.model.Role;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Carries everything Care needs to grant sessions and credit referrals, so it never reads plan tables.
 * Care uses expiresAt as given rather than recalculating it, because delivery can be hours late.
 */
public record PaymentSucceededEvent(
        String eventId,
        int schemaVersion,
        Long orderId,
        Long userId,
        PlanCode planCode,
        int months,
        BigDecimal price,
        LocalDateTime paidAt,
        LocalDateTime expiresAt,
        List<SessionAllocation> sessions
) {
    public record SessionAllocation(Role professionalType, int sessionCount) {}
}
