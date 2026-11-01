package com.narturebelle.payments.service;

import com.narturebelle.payments.dto.FinanceSummaryResponse;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
import com.narturebelle.payments.model.PlanCode;
import com.narturebelle.payments.model.Refund;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.repository.RefundRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Honest by design: there's no session-consumption mechanism yet (that's Phase 3's booking/session
 * flow), so deferredRevenueLiability is reported as exactly equal to netRevenue — 100% of every paid,
 * non-fully-refunded order's value, not a fabricated partial-usage estimate.
 */
@Component
public class FinanceService {

    private static final List<OrderStatus> REVENUE_STATUSES =
            List.of(OrderStatus.PAID, OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED);

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final PlanService planService;

    public FinanceService(OrderRepository orderRepository, RefundRepository refundRepository, PlanService planService) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.planService = planService;
    }

    public FinanceSummaryResponse summary() {
        List<Order> revenueOrders = orderRepository.findByStatusIn(REVENUE_STATUSES);

        BigDecimal grossRevenue = revenueOrders.stream()
                .map(Order::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefunded = revenueOrders.stream()
                .flatMap(order -> refundRepository.findByOrderId(order.getId()).stream())
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<PlanCode, List<Order>> ordersByPlan = revenueOrders.stream()
                .collect(Collectors.groupingBy(this::resolvePlanCode));

        List<FinanceSummaryResponse.PlanRevenue> revenueByPlan = ordersByPlan.entrySet().stream()
                .map(e -> new FinanceSummaryResponse.PlanRevenue(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(Order::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(r -> r.planCode().name()))
                .toList();

        return FinanceSummaryResponse.of(revenueOrders.size(), grossRevenue, totalRefunded, revenueByPlan);
    }

    private PlanCode resolvePlanCode(Order order) {
        return planService.resolvePlanCode(order.getPlanVersionId())
                .orElseThrow(() -> new IllegalStateException("Order references an unresolvable PlanVersion: " + order.getPlanVersionId()));
    }
}
