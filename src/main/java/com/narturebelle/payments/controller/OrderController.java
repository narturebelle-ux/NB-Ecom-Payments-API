package com.narturebelle.payments.controller;

import com.narturebelle.payments.dto.CreateOrderRequest;
import com.narturebelle.payments.dto.OrderResponse;
import com.narturebelle.payments.exception.UserProfileNotFoundException;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.security.AuthorizationService;
import com.narturebelle.payments.security.FirebaseUserPrincipal;
import com.narturebelle.payments.security.RequireVerified;
import com.narturebelle.payments.service.OrderService;
import com.narturebelle.payments.service.PlanService;
import com.narturebelle.payments.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final PlanService planService;
    private final RefundService refundService;
    private final AuthorizationService authorizationService;

    public OrderController(OrderService orderService, PlanService planService, RefundService refundService,
                            AuthorizationService authorizationService) {
        this.orderService = orderService;
        this.planService = planService;
        this.refundService = refundService;
        this.authorizationService = authorizationService;
    }

    @RequireVerified
    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody CreateOrderRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        Order order = orderService.createOrder(userId, req, idempotencyKey);
        return ResponseEntity.ok(toResponse(order));
    }

    @RequireVerified
    @GetMapping
    public ResponseEntity<List<OrderResponse>> list() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        List<OrderResponse> orders = orderService.listForUser(userId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @RequireVerified
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOne(@PathVariable Long id) {
        Order order = orderService.find(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(order.getUserId());
        return ResponseEntity.ok(toResponse(order));
    }

    private OrderResponse toResponse(Order order) {
        com.narturebelle.payments.model.PlanCode planCode = planService.resolvePlanCode(order.getPlanVersionId()).orElse(null);
        return OrderResponse.from(order, planCode, orderService.installmentsFor(order.getId()),
                refundService.listForOrder(order.getId()));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
