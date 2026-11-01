package com.narturebelle.payments.payment;

/** A webhook-shaped JSON body plus its signature header, as a real gateway delivery would carry. */
public record SignedPayload(String rawBody, String signatureHeader) {
}
