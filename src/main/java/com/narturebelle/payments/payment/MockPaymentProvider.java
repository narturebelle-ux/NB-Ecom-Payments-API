package com.narturebelle.payments.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.Payment;
import com.narturebelle.payments.model.PaymentProviderType;
import com.narturebelle.payments.model.WebhookEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 2's only PaymentProvider. Signs/verifies with HMAC-SHA256 using payments.mock.webhook-secret
 * so the mock "Pay successfully"/"Fail payment" buttons exercise the exact same signature-verification
 * and parsing code a real Phase 7 gateway delivery would hit, not just the business-logic tail end.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public MockPaymentProvider(ObjectMapper objectMapper,
                                @Value("${payments.mock.webhook-secret}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public CheckoutSession initiateCheckout(Payment payment, Order order) {
        String reference = "mock_" + UUID.randomUUID();
        return new CheckoutSession(reference, Map.of("provider", "MOCK"));
    }

    @Override
    public boolean verifySignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null) return false;
        String expected = sign(rawBody);
        return constantTimeEquals(expected, signatureHeader);
    }

    @Override
    public WebhookEventPayload parseEvent(String rawBody) {
        try {
            Map<String, Object> raw = objectMapper.readValue(rawBody,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
            return new WebhookEventPayload(
                    (String) raw.get("eventId"),
                    WebhookEventType.valueOf((String) raw.get("type")),
                    Long.valueOf(raw.get("paymentId").toString()),
                    (String) raw.get("providerReference"),
                    new java.math.BigDecimal(raw.get("amount").toString()),
                    LocalDateTime.parse((String) raw.get("occurredAt"))
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed webhook payload: " + e.getMessage(), e);
        }
    }

    /** Mock-only — not part of the PaymentProvider interface since a real gateway never needs this. */
    public SignedPayload buildMockEvent(Payment payment, WebhookEventType type) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("type", type.name());
            event.put("paymentId", payment.getId());
            event.put("providerReference", payment.getProviderPaymentReference());
            event.put("amount", payment.getAmount());
            event.put("occurredAt", LocalDateTime.now().toString());
            String rawBody = objectMapper.writeValueAsString(event);
            return new SignedPayload(rawBody, sign(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build mock webhook event", e);
        }
    }

    public PaymentProviderType type() {
        return PaymentProviderType.MOCK;
    }

    private String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
