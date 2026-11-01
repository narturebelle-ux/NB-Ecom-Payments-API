package com.narturebelle.payments.security;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class DefaultFirebaseTokenVerifier implements FirebaseTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultFirebaseTokenVerifier.class);

    private final Environment environment;

    public DefaultFirebaseTokenVerifier(Environment environment) {
        this.environment = environment;
    }

    @Override
    public VerifiedFirebaseToken verify(String idToken) throws Exception {
        if (FirebaseApp.getApps().isEmpty()) {
            return devBypass();
        }

        FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken, false);
        String phone = null;
        Object claim = decoded.getClaims().get("phone_number");
        if (claim instanceof String s && !s.isBlank()) {
            phone = s;
        }
        return new VerifiedFirebaseToken(decoded.getUid(), decoded.getEmail(), decoded.isEmailVerified(), phone, decoded.getName());
    }

    private VerifiedFirebaseToken devBypass() {
        if (environment.acceptsProfiles(Profiles.of("prod", "stage"))) {
            // Should be unreachable: FirebaseConfig fails application startup in prod/stage
            // when Firebase can't be initialized. This is a defense-in-depth guard only.
            throw new IllegalStateException("Firebase is not initialized in prod/stage — refusing dev bypass.");
        }
        log.warn(">>> DEV BYPASS: Firebase Admin SDK is not configured. Treating this request as a fake, " +
                "pre-verified dev user. This must never happen in prod/stage.");
        return new VerifiedFirebaseToken("dev-bypass-uid", "dev@localhost", true, null, null);
    }
}
