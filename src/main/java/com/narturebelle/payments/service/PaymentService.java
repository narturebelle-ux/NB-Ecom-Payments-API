package com.narturebelle.payments.service;

import com.narturebelle.payments.exception.InvalidWebhookSignatureException;
import com.narturebelle.payments.exception.PaymentNotAllowedException;
import com.narturebelle.payments.exception.PaymentNotFoundException;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
import com.narturebelle.payments.model.Payment;
import com.narturebelle.payments.model.PaymentProviderType;
import com.narturebelle.payments.model.PaymentStatus;
import com.narturebelle.payments.model.PaymentWebhookEvent;
import com.narturebelle.payments.model.PlanValidity;
import com.narturebelle.payments.model.PlanVersion;
import com.narturebelle.payments.model.WebhookEventType;
import com.narturebelle.payments.outbox.OutboxService;
import com.narturebelle.payments.outbox.PaymentSucceededEvent;
import com.narturebelle.payments.payment.CheckoutSession;
import com.narturebelle.payments.payment.PaymentProvider;
import com.narturebelle.payments.payment.WebhookEventPayload;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.repository.PaymentRepository;
import com.narturebelle.payments.repository.PaymentWebhookEventRepository;
import com.narturebelle.payments.repository.PlanValidityRepository;
import com.narturebelle.payments.security.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;
    private final PlanService planService;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final PlanValidityRepository planValidityRepository;

    public PaymentService(PaymentRepository paymentRepository, PaymentWebhookEventRepository paymentWebhookEventRepository,
                           OrderRepository orderRepository, PaymentProvider paymentProvider,
                           PlanService planService, AuditService auditService,
                           OutboxService outboxService, PlanValidityRepository planValidityRepository) {
        this.paymentRepository = paymentRepository;
        this.planValidityRepository = planValidityRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.orderRepository = orderRepository;
        this.paymentProvider = paymentProvider;
        this.planService = planService;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    public Payment createPayment(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentNotAllowedException("Order is not payable in its current status: " + order.getStatus());
        }

        List<Payment> existing = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        Optional<Payment> stillCreated = existing.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .findFirst();
        if (stillCreated.isPresent()) {
            return stillCreated.get();
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider(PaymentProviderType.MOCK);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setAmount(order.getPrice());

        CheckoutSession session = paymentProvider.initiateCheckout(payment, order);
        payment.setProviderPaymentReference(session.providerPaymentReference());
        Payment saved = paymentRepository.save(payment);

        auditService.record("PAYMENT_CREATED", "Payment", String.valueOf(saved.getId()), null,
                Map.of("orderId", order.getId(), "amount", saved.getAmount().toString()));

        return saved;
    }

    @Transactional
    public void processWebhook(String rawBody, String signatureHeader) {
        if (!paymentProvider.verifySignature(rawBody, signatureHeader)) {
            throw new InvalidWebhookSignatureException("Webhook signature verification failed.");
        }

        WebhookEventPayload event = paymentProvider.parseEvent(rawBody);

        if (paymentWebhookEventRepository.existsByProviderEventId(event.eventId())) {
            return; // already processed -- idempotent no-op
        }

        PaymentWebhookEvent ledgerRow = new PaymentWebhookEvent();
        ledgerRow.setPaymentId(event.paymentId());
        ledgerRow.setProviderEventId(event.eventId());
        ledgerRow.setEventType(event.type().name());
        ledgerRow.setRawPayload(rawBody);
        try {
            paymentWebhookEventRepository.save(ledgerRow);
        } catch (DataIntegrityViolationException alreadyDelivered) {
            return; // lost a race to a concurrent delivery of the identical event
        }

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException("Unknown payment: " + event.paymentId()));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Payment references a missing order: " + payment.getOrderId()));

        if (event.type() == WebhookEventType.PAYMENT_SUCCEEDED) {
            handleSucceeded(payment, order);
        } else {
            handleFailed(payment, order);
        }
    }

    private void handleSucceeded(Payment payment, Order order) {
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);
            auditService.record("PAYMENT_SUCCEEDED", "Payment", String.valueOf(payment.getId()), null);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            auditService.record("ORDER_PAID", "Order", String.valueOf(order.getId()), null);
            // Sessions and referral credit are Care's: the event is saved with this payment, so neither can be lost.
            enqueuePaymentSucceeded(order);
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            // Replaced by a newer checkout, or already failed: money arrived for an order that's closed. Flag it for
            // a person to refund rather than granting a second plan.
            auditService.record("PAYMENT_ON_CLOSED_ORDER", "Order", String.valueOf(order.getId()), "Payment succeeded on a closed order");
        }
    }

    private void enqueuePaymentSucceeded(Order order) {
        PlanVersion planVersion = planService.findVersionById(order.getPlanVersionId())
                .orElseThrow(() -> new IllegalStateException("Order references a missing PlanVersion: " + order.getPlanVersionId()));
        List<PaymentSucceededEvent.SessionAllocation> sessions = planService.allocationsFor(planVersion.getId()).stream()
                .map(allocation -> new PaymentSucceededEvent.SessionAllocation(allocation.getProfessionalType(),
                        PlanPricing.prorateSessions(allocation.getSessionCount(), order.effectiveMonths())))
                .toList();
        // To the column's precision, so the event carries exactly what's stored.
        LocalDateTime paidAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiresAt = paidAt.plusDays(planVersion.getValidityDays());
        planValidityRepository.save(new PlanValidity(order.getId(), paidAt, expiresAt));

        String eventId = "payment-succeeded:" + order.getId();
        outboxService.enqueue(eventId, "PaymentSucceeded", new PaymentSucceededEvent(
                eventId, 1, order.getId(), order.getUserId(), planService.resolvePlanCode(planVersion.getId()).orElseThrow(),
                order.effectiveMonths(), order.getPrice(), paidAt, expiresAt, sessions));
    }

    private void handleFailed(Payment payment, Order order) {
        if (payment.getStatus() != PaymentStatus.FAILED) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            auditService.record("PAYMENT_FAILED", "Payment", String.valueOf(payment.getId()), null);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            auditService.record("ORDER_FAILED", "Order", String.valueOf(order.getId()), null);
        }
    }

    public Optional<Payment> find(Long id) {
        return paymentRepository.findById(id);
    }
}
