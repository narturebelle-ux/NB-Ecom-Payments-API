package com.narturebelle.payments.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A versioned price/content snapshot of a Plan. Orders pin to a specific PlanVersion id so a later
 * price edit (a new version row, active flips over) never retroactively changes an existing order.
 * Per-professional session allocation is PlanEntitlement.
 */
@Entity
@Table(name = "plan_versions")
public class PlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private BigDecimal oneTimePrice;

    @Column(nullable = false)
    private BigDecimal installmentTotalPrice;

    @Column(nullable = false)
    private Integer installmentCount;

    @Column(nullable = false)
    private Integer totalSessions;

    @Column(nullable = false)
    private Integer validityDays;

    @Column(nullable = false)
    private LocalDateTime effectiveAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_version_inclusions", joinColumns = @JoinColumn(name = "plan_version_id"))
    @Column(name = "inclusion", length = 1000)
    private List<String> inclusions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_version_exclusions", joinColumns = @JoinColumn(name = "plan_version_id"))
    @Column(name = "exclusion", length = 1000)
    private List<String> exclusions = new ArrayList<>();

    public PlanVersion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public BigDecimal getOneTimePrice() { return oneTimePrice; }
    public void setOneTimePrice(BigDecimal oneTimePrice) { this.oneTimePrice = oneTimePrice; }

    public BigDecimal getInstallmentTotalPrice() { return installmentTotalPrice; }
    public void setInstallmentTotalPrice(BigDecimal installmentTotalPrice) { this.installmentTotalPrice = installmentTotalPrice; }

    public Integer getInstallmentCount() { return installmentCount; }
    public void setInstallmentCount(Integer installmentCount) { this.installmentCount = installmentCount; }

    public Integer getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Integer totalSessions) { this.totalSessions = totalSessions; }

    public Integer getValidityDays() { return validityDays; }
    public void setValidityDays(Integer validityDays) { this.validityDays = validityDays; }

    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDateTime effectiveAt) { this.effectiveAt = effectiveAt; }

    public List<String> getInclusions() { return inclusions; }
    public void setInclusions(List<String> inclusions) { this.inclusions = inclusions; }

    public List<String> getExclusions() { return exclusions; }
    public void setExclusions(List<String> exclusions) { this.exclusions = exclusions; }
}
