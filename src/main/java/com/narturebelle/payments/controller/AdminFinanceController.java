package com.narturebelle.payments.controller;

import com.narturebelle.payments.dto.FinanceSummaryResponse;
import com.narturebelle.payments.service.FinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/finance")
public class AdminFinanceController {

    private final FinanceService financeService;

    public AdminFinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryResponse> summary() {
        return ResponseEntity.ok(financeService.summary());
    }
}
