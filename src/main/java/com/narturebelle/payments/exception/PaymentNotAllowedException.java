package com.narturebelle.payments.exception;

/** Thrown when creating a payment for an order that isn't PENDING. Maps to 409. */
public class PaymentNotAllowedException extends RuntimeException {
    public PaymentNotAllowedException(String message) {
        super(message);
    }
}
