package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, Long> {
    Optional<PlanVersion> findByPlanIdAndActiveTrue(Long planId);
    List<PlanVersion> findByActiveTrueOrderByPlanIdAsc();
}
