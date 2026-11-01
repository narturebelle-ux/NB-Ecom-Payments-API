package com.narturebelle.payments.dto;

import com.narturebelle.payments.model.PlanCode;
import com.narturebelle.payments.model.PlanEntitlement;
import com.narturebelle.payments.model.PlanVersion;
import com.narturebelle.payments.model.Role;
import com.narturebelle.payments.service.PlanPricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * A plan at its constant listed price (see PlanPricing). months is always 9 and fullOneTimePrice equals
 * oneTimePrice; both stay so existing clients keep working. monthlyPrice is the listed price / 9.
 */
public class PlanResponse {

    private Long id;
    private PlanCode code;
    private String name;
    private Integer months;
    private BigDecimal monthlyPrice;
    private BigDecimal fullOneTimePrice;
    private BigDecimal oneTimePrice;
    private BigDecimal installmentTotalPrice;
    private Integer installmentCount;
    private Integer totalSessions;
    private Integer validityDays;
    private List<String> inclusions;
    private List<String> exclusions;
    private List<AllocationItem> allocations;

    public static PlanResponse from(Long planId, PlanCode code, String name, PlanVersion version,
                                     List<PlanEntitlement> allocations) {
        PlanResponse dto = new PlanResponse();
        dto.id = planId;
        dto.code = code;
        dto.name = name;
        dto.months = PlanPricing.FULL_MONTHS;
        dto.monthlyPrice = PlanPricing.monthlyRate(version.getOneTimePrice());
        dto.fullOneTimePrice = version.getOneTimePrice();
        dto.oneTimePrice = version.getOneTimePrice();
        dto.installmentTotalPrice = version.getInstallmentTotalPrice();
        dto.installmentCount = version.getInstallmentCount();
        dto.validityDays = version.getValidityDays();
        dto.inclusions = version.getInclusions();
        dto.exclusions = version.getExclusions();
        dto.allocations = allocations.stream()
                .map(a -> new AllocationItem(a.getProfessionalType(), a.getSessionCount()))
                .toList();
        dto.totalSessions = version.getTotalSessions();
        return dto;
    }

    public Long getId() { return id; }
    public PlanCode getCode() { return code; }
    public String getName() { return name; }
    public Integer getMonths() { return months; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getFullOneTimePrice() { return fullOneTimePrice; }
    public BigDecimal getOneTimePrice() { return oneTimePrice; }
    public BigDecimal getInstallmentTotalPrice() { return installmentTotalPrice; }
    public Integer getInstallmentCount() { return installmentCount; }
    public Integer getTotalSessions() { return totalSessions; }
    public Integer getValidityDays() { return validityDays; }
    public List<String> getInclusions() { return inclusions; }
    public List<String> getExclusions() { return exclusions; }
    public List<AllocationItem> getAllocations() { return allocations; }

    public record AllocationItem(Role professionalType, Integer sessionCount) {}
}
