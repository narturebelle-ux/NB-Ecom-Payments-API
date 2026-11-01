package com.narturebelle.payments.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** When a paid plan ends, worked out once at payment so Money and Care's sessions share one value, however late the event arrives. */
@Entity
@Table(name = "plan_validity", schema = "money")
public class PlanValidity {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public PlanValidity() {}

    public PlanValidity(Long orderId, LocalDateTime paidAt, LocalDateTime expiresAt) {
        this.orderId = orderId;
        this.paidAt = paidAt;
        this.expiresAt = expiresAt;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
