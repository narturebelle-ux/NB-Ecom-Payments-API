package com.narturebelle.payments.security;

/** Abstraction over Firebase ID token verification, so tests can substitute a fake without real credentials. */
public interface FirebaseTokenVerifier {
    VerifiedFirebaseToken verify(String idToken) throws Exception;
}
