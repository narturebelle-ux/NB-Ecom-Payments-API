package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.Plan;
import com.narturebelle.payments.model.PlanCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(PlanCode code);
}
