package com.narturebelle.payments.payment;

import java.util.Map;

/** What a provider hands back after initiating checkout. checkoutMeta is provider-specific (e.g. the mock action paths). */
public record CheckoutSession(String providerPaymentReference, Map<String, Object> checkoutMeta) {
}
