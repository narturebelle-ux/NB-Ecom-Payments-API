package com.narturebelle.payments.care;

/** The Care API couldn't answer. Maps to 503: it says nothing about the caller, so it must never become a 401. */
public class CareUnavailableException extends RuntimeException {

    public CareUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
