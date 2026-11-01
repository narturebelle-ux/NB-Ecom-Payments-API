package com.narturebelle.payments.controller;

import com.narturebelle.payments.dto.CreateRefundRequest;
import com.narturebelle.payments.dto.RefundResponse;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.Refund;
import com.narturebelle.payments.security.AuthorizationService;
import com.narturebelle.payments.service.OrderService;
import com.narturebelle.payments.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) — no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final RefundService refundService;
    private final AuthorizationService authorizationService;

    public AdminOrderController(OrderService orderService, RefundService refundService,
                                 AuthorizationService authorizationService) {
        this.orderService = orderService;
        this.refundService = refundService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{orderId}/refunds")
    public ResponseEntity<RefundResponse> createRefund(@PathVariable Long orderId,
                                                         @Valid @RequestBody CreateRefundRequest req) {
        Order order = orderService.find(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Long adminUserId = authorizationService.currentPrincipal().userId();
        Refund refund = refundService.createRefund(order, req, adminUserId);
        return ResponseEntity.ok(RefundResponse.from(refund));
    }
}
