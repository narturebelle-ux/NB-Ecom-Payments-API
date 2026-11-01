package com.narturebelle.payments.exception;

/** Thrown when a refund is requested for a non-refundable order or exceeds the remaining refundable amount. Maps to 409. */
public class RefundNotAllowedException extends RuntimeException {
    public RefundNotAllowedException(String message) {
        super(message);
    }
}
