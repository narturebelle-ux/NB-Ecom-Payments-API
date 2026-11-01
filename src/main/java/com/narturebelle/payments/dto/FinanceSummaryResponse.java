package com.narturebelle.payments.dto;

import com.narturebelle.payments.model.PlanCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * deferredRevenueLiability is honestly equal to netRevenue: there's no session-consumption mechanism
 * yet (Phase 3), so 100% of every paid, non-fully-refunded entitlement is "unused" by definition.
 */
public class FinanceSummaryResponse {

    private long paidOrderCount;
    private BigDecimal grossRevenue;
    private BigDecimal totalRefunded;
    private BigDecimal netRevenue;
    private List<PlanRevenue> revenueByPlan;
    private BigDecimal deferredRevenueLiability;
    private String deferredRevenueMethodologyNote;

    public static FinanceSummaryResponse of(long paidOrderCount, BigDecimal grossRevenue, BigDecimal totalRefunded,
                                              List<PlanRevenue> revenueByPlan) {
        FinanceSummaryResponse dto = new FinanceSummaryResponse();
        dto.paidOrderCount = paidOrderCount;
        dto.grossRevenue = grossRevenue;
        dto.totalRefunded = totalRefunded;
        dto.netRevenue = grossRevenue.subtract(totalRefunded);
        dto.revenueByPlan = revenueByPlan;
        dto.deferredRevenueLiability = dto.netRevenue;
        dto.deferredRevenueMethodologyNote = "Phase 3 session consumption does not exist yet, so 100% of net "
                + "collected revenue is counted as deferred/unused-session liability. This number becomes "
                + "meaningful once bookings start consuming entitlements.";
        return dto;
    }

    public long getPaidOrderCount() { return paidOrderCount; }
    public BigDecimal getGrossRevenue() { return grossRevenue; }
    public BigDecimal getTotalRefunded() { return totalRefunded; }
    public BigDecimal getNetRevenue() { return netRevenue; }
    public List<PlanRevenue> getRevenueByPlan() { return revenueByPlan; }
    public BigDecimal getDeferredRevenueLiability() { return deferredRevenueLiability; }
    public String getDeferredRevenueMethodologyNote() { return deferredRevenueMethodologyNote; }

    public record PlanRevenue(PlanCode planCode, long paidOrderCount, BigDecimal grossRevenue) {}
}
