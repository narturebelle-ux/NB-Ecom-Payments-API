package com.narturebelle.payments.security;

/** Our own shape for a verified Firebase ID token, decoupled from the SDK's FirebaseToken class. */
public record VerifiedFirebaseToken(String uid, String email, boolean emailVerified, String phone, String name) {
}
