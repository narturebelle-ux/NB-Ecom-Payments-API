package com.narturebelle.payments.exception;

/** A request that is well-formed but not allowed right now. Maps to 409 with its code: PLAN_ALREADY_ACTIVE,
 * SLOT_IN_PAST, SLOT_AFTER_PLAN_EXPIRY, SESSION_NOT_FOR_STAGE, BOOKING_ALREADY_STARTED. */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
