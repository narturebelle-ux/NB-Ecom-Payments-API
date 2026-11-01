package com.narturebelle.payments.controller;

import com.narturebelle.payments.dto.CreatePaymentRequest;
import com.narturebelle.payments.dto.PaymentResponse;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.Payment;
import com.narturebelle.payments.model.WebhookEventType;
import com.narturebelle.payments.payment.MockPaymentProvider;
import com.narturebelle.payments.payment.PaymentProvider;
import com.narturebelle.payments.payment.SignedPayload;
import com.narturebelle.payments.security.AuthorizationService;
import com.narturebelle.payments.security.RequireVerified;
import com.narturebelle.payments.service.OrderService;
import com.narturebelle.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentProvider paymentProvider;
    private final AuthorizationService authorizationService;

    public PaymentController(PaymentService paymentService, OrderService orderService, PaymentProvider paymentProvider,
                              AuthorizationService authorizationService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.paymentProvider = paymentProvider;
        this.authorizationService = authorizationService;
    }

    @RequireVerified
    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest req) {
        Order order = orderService.find(req.getOrderId()).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(order.getUserId());

        Payment payment = paymentService.createPayment(order);
        return ResponseEntity.ok(PaymentResponse.from(payment,
                "/api/v1/payments/" + payment.getId() + "/mock/succeed",
                "/api/v1/payments/" + payment.getId() + "/mock/fail"));
    }

    /** Fully public — a real Phase 7 gateway delivery carries no Firebase token. Signature verification is the only guard. */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody String rawBody,
                                                         @RequestHeader("X-Webhook-Signature") String signature) {
        paymentService.processWebhook(rawBody, signature);
        return ResponseEntity.ok(Map.of("received", true));
    }

    @RequireVerified
    @PostMapping("/{paymentId}/mock/succeed")
    public ResponseEntity<Map<String, Object>> mockSucceed(@PathVariable Long paymentId) {
        return mockEvent(paymentId, WebhookEventType.PAYMENT_SUCCEEDED);
    }

    @RequireVerified
    @PostMapping("/{paymentId}/mock/fail")
    public ResponseEntity<Map<String, Object>> mockFail(@PathVariable Long paymentId) {
        return mockEvent(paymentId, WebhookEventType.PAYMENT_FAILED);
    }

    private ResponseEntity<Map<String, Object>> mockEvent(Long paymentId, WebhookEventType type) {
        if (!(paymentProvider instanceof MockPaymentProvider mock)) {
            // Safety net: once a real Phase 7 gateway replaces MockPaymentProvider, these buttons stop existing.
            return ResponseEntity.notFound().build();
        }
        Payment payment = paymentService.find(paymentId).orElse(null);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        Order order = orderService.find(payment.getOrderId()).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(order.getUserId());

        SignedPayload signed = mock.buildMockEvent(payment, type);
        paymentService.processWebhook(signed.rawBody(), signed.signatureHeader());
        return ResponseEntity.ok(Map.of("status", type.name()));
    }
}
