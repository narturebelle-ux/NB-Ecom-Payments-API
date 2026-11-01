package com.narturebelle.payments.service;

import com.narturebelle.payments.dto.CreateRefundRequest;
import com.narturebelle.payments.exception.RefundNotAllowedException;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
import com.narturebelle.payments.model.Refund;
import com.narturebelle.payments.outbox.OutboxService;
import com.narturebelle.payments.outbox.RefundIssuedEvent;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.repository.RefundRepository;
import com.narturebelle.payments.security.AuditService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Refunds are always an admin-entered amount, never auto-calculated from a policy — the
 * refund/unused-session policy is explicitly deferred (product owner decision). This service is a
 * pure financial ledger: it never touches Entitlement rows.
 */
@Component
public class RefundService {

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final AuditService auditService;

    private final OutboxService outboxService;

    public RefundService(OrderRepository orderRepository, RefundRepository refundRepository, AuditService auditService,
                          OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    @Transactional
    public Refund createRefund(Order order, CreateRefundRequest req, Long adminUserId) {
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PARTIALLY_REFUNDED) {
            throw new RefundNotAllowedException("Order is not in a refundable state: " + order.getStatus());
        }

        BigDecimal alreadyRefunded = refundRepository.findByOrderId(order.getId()).stream()
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = order.getPrice().subtract(alreadyRefunded);

        if (req.getAmount().compareTo(remaining) > 0) {
            throw new RefundNotAllowedException("Refund amount exceeds the remaining refundable amount of " + remaining);
        }

        Refund refund = new Refund();
        refund.setOrderId(order.getId());
        refund.setAmount(req.getAmount());
        refund.setReason(req.getReason());
        refund.setAdminUserId(adminUserId);
        Refund saved = refundRepository.save(refund);

        BigDecimal totalRefunded = alreadyRefunded.add(req.getAmount());
        order.setStatus(totalRefunded.compareTo(order.getPrice()) >= 0
                ? OrderStatus.REFUNDED
                : OrderStatus.PARTIALLY_REFUNDED);
        orderRepository.save(order);

        auditService.record("REFUND_RECORDED", "Order", String.valueOf(order.getId()), req.getReason(),
                Map.of("amount", req.getAmount().toString(), "newOrderStatus", order.getStatus().name()));

        String eventId = "refund-issued:" + saved.getId();
        outboxService.enqueue(eventId, "RefundIssued", new RefundIssuedEvent(
                eventId, 1, saved.getId(), order.getId(), order.getUserId(), saved.getAmount(), order.getStatus()));

        return saved;
    }

    public List<Refund> listForOrder(Long orderId) {
        return refundRepository.findByOrderId(orderId);
    }
}
