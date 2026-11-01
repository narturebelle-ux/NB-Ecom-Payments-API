package com.narturebelle.payments.model;

import jakarta.persistence.*;

/** The per-professional session allocation TEMPLATE for a PlanVersion. Copied into real Entitlement rows once an order is paid. */
@Entity
@Table(name = "plan_entitlements")
public class PlanEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role professionalType;

    @Column(nullable = false)
    private Integer sessionCount;

    public PlanEntitlement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(Long planVersionId) { this.planVersionId = planVersionId; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public Integer getSessionCount() { return sessionCount; }
    public void setSessionCount(Integer sessionCount) { this.sessionCount = sessionCount; }
}
