package com.narturebelle.payments.exception;

/** Thrown by @RequireVerified-guarded endpoints when the current user isn't verified. */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
