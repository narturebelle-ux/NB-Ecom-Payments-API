package com.narturebelle.payments.controller;

import com.narturebelle.payments.dto.PlanResponse;
import com.narturebelle.payments.model.Plan;
import com.narturebelle.payments.model.PlanVersion;
import com.narturebelle.payments.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> list() {
        List<PlanResponse> plans = planService.listAllOrdered().stream()
                .map(this::toResponse)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getOne(@PathVariable Long id) {
        Plan plan = planService.findPlan(id).orElse(null);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        PlanResponse response = toResponse(plan);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    private PlanResponse toResponse(Plan plan) {
        PlanVersion activeVersion = planService.findActiveVersion(plan.getId()).orElse(null);
        if (activeVersion == null) {
            return null;
        }
        return PlanResponse.from(plan.getId(), plan.getCode(), plan.getName(), activeVersion,
                planService.allocationsFor(activeVersion.getId()));
    }
}
