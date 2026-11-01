package com.narturebelle.payments.exception;

/** Thrown when a payment webhook's signature doesn't verify. Maps to 401. */
public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
