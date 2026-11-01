package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.PlanEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, Long> {
    List<PlanEntitlement> findByPlanVersionId(Long planVersionId);
}
