package com.narturebelle.payments.dto;

import com.narturebelle.payments.model.InstallmentSchedule;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InstallmentScheduleResponse {

    private Integer installmentNumber;
    private BigDecimal amount;
    private LocalDate dueDate;

    public static InstallmentScheduleResponse from(InstallmentSchedule schedule) {
        InstallmentScheduleResponse dto = new InstallmentScheduleResponse();
        dto.installmentNumber = schedule.getInstallmentNumber();
        dto.amount = schedule.getAmount();
        dto.dueDate = schedule.getDueDate();
        return dto;
    }

    public Integer getInstallmentNumber() { return installmentNumber; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDueDate() { return dueDate; }
}
