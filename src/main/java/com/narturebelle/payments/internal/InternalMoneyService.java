package com.narturebelle.payments.internal;

import com.narturebelle.payments.exception.PlanNotFoundException;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
import com.narturebelle.payments.model.PaymentMode;
import com.narturebelle.payments.model.Plan;
import com.narturebelle.payments.model.PlanCode;
import com.narturebelle.payments.model.PlanValidity;
import com.narturebelle.payments.model.PlanVersion;
import com.narturebelle.payments.outbox.PaymentSucceededEvent.SessionAllocation;
import com.narturebelle.payments.repository.InstallmentScheduleRepository;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.repository.PaymentRepository;
import com.narturebelle.payments.repository.PlanRepository;
import com.narturebelle.payments.repository.PlanValidityRepository;
import com.narturebelle.payments.repository.RefundRepository;
import com.narturebelle.payments.security.AuditService;
import com.narturebelle.payments.service.OrderService;
import com.narturebelle.payments.service.PlanPricing;
import com.narturebelle.payments.service.PlanService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class InternalMoneyService {

    public record RewardRequest(@NotBlank String rewardKey, @NotNull Long userId, @NotNull PlanCode planCode) {}

    public record RewardOrder(Long orderId, Long planVersionId, PlanCode planCode, LocalDateTime expiresAt, List<SessionAllocation> sessions) {}

    public record OrderSummary(boolean hasAnyOrder, boolean hasPaidOrder, boolean hasPaidOrderAboveZero) {}

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final InstallmentScheduleRepository installmentScheduleRepository;
    private final PlanRepository planRepository;
    private final PlanValidityRepository planValidityRepository;
    private final PlanService planService;
    private final OrderService orderService;
    private final AuditService auditService;

    public InternalMoneyService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                RefundRepository refundRepository, InstallmentScheduleRepository installmentScheduleRepository,
                                PlanRepository planRepository, PlanValidityRepository planValidityRepository,
                                PlanService planService, OrderService orderService, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.installmentScheduleRepository = installmentScheduleRepository;
        this.planRepository = planRepository;
        this.planValidityRepository = planValidityRepository;
        this.planService = planService;
        this.orderService = orderService;
        this.auditService = auditService;
    }

    /** Care's rewardKey is the order's idempotency key, so a reward granted before the move isn't granted again. */
    @Transactional
    public RewardOrder grantReward(RewardRequest request) {
        Order order = orderRepository.findByUserIdAndIdempotencyKey(request.userId(), request.rewardKey())
                .orElseGet(() -> createRewardOrder(request));
        PlanVersion planVersion = planService.findVersionById(order.getPlanVersionId())
                .orElseThrow(() -> new IllegalStateException("Order references a missing PlanVersion: " + order.getPlanVersionId()));
        List<SessionAllocation> sessions = planService.allocationsFor(planVersion.getId()).stream()
                .map(allocation -> new SessionAllocation(allocation.getProfessionalType(),
                        PlanPricing.prorateSessions(allocation.getSessionCount(), order.effectiveMonths())))
                .toList();
        return new RewardOrder(order.getId(), planVersion.getId(), planService.resolvePlanCode(planVersion.getId()).orElseThrow(),
                orderService.planExpiry(order), sessions);
    }

    private Order createRewardOrder(RewardRequest request) {
        Plan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new PlanNotFoundException("No plan with code " + request.planCode()));
        PlanVersion planVersion = planService.findActiveVersion(plan.getId())
                .orElseThrow(() -> new PlanNotFoundException("No active version of plan " + request.planCode()));

        Order order = new Order();
        order.setUserId(request.userId());
        order.setPlanVersionId(planVersion.getId());
        order.setIdempotencyKey(request.rewardKey());
        order.setRequestFingerprint("referral-reward");
        order.setStatus(OrderStatus.PAID);
        order.setPaymentMode(PaymentMode.ONE_TIME);
        order.setPrice(BigDecimal.ZERO);
        order.setMonths(PlanPricing.FULL_MONTHS);
        Order saved = orderRepository.saveAndFlush(order);

        // To the column's precision, so what Care receives is exactly what's stored.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        planValidityRepository.save(new PlanValidity(saved.getId(), now, now.plusDays(planVersion.getValidityDays())));
        auditService.record("REWARD_ORDER_CREATED", "Order", String.valueOf(saved.getId()), "Free plan requested by the Care API");
        return saved;
    }

    public OrderSummary orderSummary(Long userId) {
        return new OrderSummary(
                orderRepository.existsByUserId(userId),
                orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PAID),
                orderRepository.existsByUserIdAndStatusAndPriceGreaterThan(userId, OrderStatus.PAID, BigDecimal.ZERO));
    }

    /** Her money data for the account export: orders and payments as today, plus refunds and instalment schedules. */
    public Map<String, Object> moneyExport(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("orders", orders);
        export.put("payments", orders.stream()
                .flatMap(o -> paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()).toList());
        export.put("refunds", orders.stream()
                .flatMap(o -> refundRepository.findByOrderId(o.getId()).stream()).toList());
        export.put("installmentSchedules", orders.stream()
                .flatMap(o -> installmentScheduleRepository.findByOrderIdOrderByInstallmentNumberAsc(o.getId()).stream()).toList());
        return export;
    }
}
