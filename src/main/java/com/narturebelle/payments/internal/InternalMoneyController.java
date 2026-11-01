package com.narturebelle.payments.internal;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** What the Care API needs from this API. Guarded by InternalTokenFilter. */
@RestController
@RequestMapping("/internal/v1")
public class InternalMoneyController {

    private final InternalMoneyService internalMoneyService;

    public InternalMoneyController(InternalMoneyService internalMoneyService) {
        this.internalMoneyService = internalMoneyService;
    }

    /** Safe to repeat: the same rewardKey returns the same order. */
    @PostMapping("/orders/reward")
    public InternalMoneyService.RewardOrder grantReward(@Valid @RequestBody InternalMoneyService.RewardRequest request) {
        return internalMoneyService.grantReward(request);
    }

    @GetMapping("/users/{id}/order-summary")
    public InternalMoneyService.OrderSummary orderSummary(@PathVariable Long id) {
        return internalMoneyService.orderSummary(id);
    }

    @GetMapping("/users/{id}/money-export")
    public Map<String, Object> moneyExport(@PathVariable Long id) {
        return internalMoneyService.moneyExport(id);
    }
}
