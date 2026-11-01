package com.narturebelle.payments.exception;

/** Thrown when an Idempotency-Key is reused with a different request (different plan/payment mode). Maps to 409. */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
