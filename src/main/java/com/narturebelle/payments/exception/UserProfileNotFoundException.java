package com.narturebelle.payments.exception;

/** Thrown when a valid token's user hasn't called POST /api/v1/auth/firebase yet. Maps to 404. */
public class UserProfileNotFoundException extends RuntimeException {
    public UserProfileNotFoundException(String message) {
        super(message);
    }
}
