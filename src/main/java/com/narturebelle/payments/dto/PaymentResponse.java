package com.narturebelle.payments.dto;

import com.narturebelle.payments.model.Payment;
import com.narturebelle.payments.model.PaymentProviderType;
import com.narturebelle.payments.model.PaymentStatus;

import java.math.BigDecimal;

/** mockSucceedPath/mockFailPath are only populated for the MOCK provider — null once a real Phase 7 gateway replaces it. */
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private PaymentProviderType provider;
    private String mockSucceedPath;
    private String mockFailPath;

    public static PaymentResponse from(Payment payment, String mockSucceedPath, String mockFailPath) {
        PaymentResponse dto = new PaymentResponse();
        dto.id = payment.getId();
        dto.orderId = payment.getOrderId();
        dto.status = payment.getStatus();
        dto.amount = payment.getAmount();
        dto.provider = payment.getProvider();
        dto.mockSucceedPath = mockSucceedPath;
        dto.mockFailPath = mockFailPath;
        return dto;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public PaymentProviderType getProvider() { return provider; }
    public String getMockSucceedPath() { return mockSucceedPath; }
    public String getMockFailPath() { return mockFailPath; }
}
