package com.narturebelle.payments.exception;

import java.util.List;

/** Thrown by @RequireConsent-guarded endpoints when any required consent is still pending. Maps to 403. */
public class ConsentRequiredException extends RuntimeException {

    private final List<String> pending;

    public ConsentRequiredException(List<String> pending) {
        super("Required consents are pending: " + pending);
        this.pending = pending;
    }

    public List<String> getPending() { return pending; }
}
