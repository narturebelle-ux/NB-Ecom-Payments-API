package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.InstallmentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentScheduleRepository extends JpaRepository<InstallmentSchedule, Long> {
    List<InstallmentSchedule> findByOrderIdOrderByInstallmentNumberAsc(Long orderId);
}
