package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByStatusIn(List<OrderStatus> statuses);
    boolean existsByUserId(Long userId);
    boolean existsByUserIdAndStatus(Long userId, OrderStatus status);

    /** A real purchase: PAID with a price above zero (a referral-reward order is free and doesn't count). */
    boolean existsByUserIdAndStatusAndPriceGreaterThan(Long userId, OrderStatus status, java.math.BigDecimal price);

    List<Order> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);
}
