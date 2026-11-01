package com.narturebelle.payments.dto;

import com.narturebelle.payments.model.PaymentMode;
import jakarta.validation.constraints.NotNull;

public class CreateOrderRequest {

    @NotNull
    private Long planId;

    @NotNull
    private PaymentMode paymentMode;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public PaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }
}
