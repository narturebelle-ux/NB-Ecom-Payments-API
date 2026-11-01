package com.narturebelle.payments.dto;

import java.util.List;

/** GET /me/plans/quote: every plan priced for her months. basis = DUE_DATE, or FULL_PLAN when there's no due date to count from. */
public record PlanQuoteResponse(int months, String basis, List<PlanResponse> plans) {}
