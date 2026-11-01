package com.narturebelle.payments.exception;

/** Thrown when a requested plan has no active version (or doesn't exist). Maps to 404. */
public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String message) {
        super(message);
    }
}
