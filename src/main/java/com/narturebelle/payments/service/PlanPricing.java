package com.narturebelle.payments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Plan prices are constant (product-owner decision, 2026-10-30, replacing due-date pricing): every plan
 * costs its listed price with its full sessions, whatever her due date or health answers. The listed
 * price covers 9 months, which only matters for showing a monthly rate.
 */
public final class PlanPricing {

    public static final int FULL_MONTHS = 9;

    private PlanPricing() {}

    /** Listed price / 9, to whole rupees: display only. */
    public static BigDecimal monthlyRate(BigDecimal fullPrice) {
        if (fullPrice == null) return null;
        return fullPrice.divide(BigDecimal.valueOf(FULL_MONTHS), 0, RoundingMode.HALF_UP);
    }

    /** Only for orders placed under the old due-date pricing (months below 9): they keep what they paid for. */
    public static int prorateSessions(Integer fullSessions, int months) {
        if (fullSessions == null || fullSessions == 0) return 0;
        if (months >= FULL_MONTHS) return fullSessions;
        return Math.max(1, (int) Math.round(fullSessions * months / (double) FULL_MONTHS));
    }
}
