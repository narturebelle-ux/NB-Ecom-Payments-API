package com.narturebelle.payments.payment;

import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.Payment;

/** The Phase 7 seam: a real gateway (Razorpay or similar) implements this exact interface alongside MockPaymentProvider. */
public interface PaymentProvider {

    CheckoutSession initiateCheckout(Payment payment, Order order);

    boolean verifySignature(String rawBody, String signatureHeader);

    WebhookEventPayload parseEvent(String rawBody);
}
