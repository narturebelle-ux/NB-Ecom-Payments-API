package com.narturebelle.payments.dto;

import com.narturebelle.payments.model.Refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundResponse {

    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime createdAt;

    public static RefundResponse from(Refund refund) {
        RefundResponse dto = new RefundResponse();
        dto.id = refund.getId();
        dto.orderId = refund.getOrderId();
        dto.amount = refund.getAmount();
        dto.reason = refund.getReason();
        dto.createdAt = refund.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
