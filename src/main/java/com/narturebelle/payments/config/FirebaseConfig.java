package com.narturebelle.payments.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private final ResourceLoader resourceLoader;
    private final Environment environment;

    @Value("${firebase.service-account-base64:}")
    private String serviceAccountBase64;

    @Value("${firebase.credentials.path:classpath:firebase-service-account.json}")
    private String credentialsPath;

    public FirebaseConfig(ResourceLoader resourceLoader, Environment environment) {
        this.resourceLoader = resourceLoader;
        this.environment = environment;
    }

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized.");
            return;
        }

        if (tryBase64Credentials() || tryLocalCredentialsFile() || tryApplicationDefaultCredentials()) {
            return;
        }

        if (isProdOrStage()) {
            throw new IllegalStateException(
                    "Firebase Admin SDK could not be initialized in profile '" + String.join(",", environment.getActiveProfiles()) +
                    "'. Set FIREBASE_SERVICE_ACCOUNT_BASE64 (or attach Application Default Credentials). " +
                    "Token verification must never be skipped in prod/stage.");
        }

        log.warn(">>> [FIREBASE NOTICE]: No credentials found (FIREBASE_SERVICE_ACCOUNT_BASE64, local file, or ADC).");
        log.warn(">>> For local dev: generate a service account JSON (Firebase Console -> Project Settings -> Service Accounts)");
        log.warn(">>> and either base64-encode it into FIREBASE_SERVICE_ACCOUNT_BASE64, or place it at");
        log.warn(">>> 'NB-Ecom-Payments-API/src/main/resources/firebase-service-account.json' (gitignored).");
        log.warn(">>> The application will run in DEV-BYPASS mode until then — all requests will be treated");
        log.warn(">>> as a fake verified dev user. This is NEVER permitted in prod/stage.");
    }

    private boolean tryBase64Credentials() {
        if (serviceAccountBase64 == null || serviceAccountBase64.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64.trim());
            try (InputStream is = new ByteArrayInputStream(decoded)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info(">>> Firebase Admin SDK initialized from FIREBASE_SERVICE_ACCOUNT_BASE64.");
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK from FIREBASE_SERVICE_ACCOUNT_BASE64: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryLocalCredentialsFile() {
        Resource resource = resourceLoader.getResource(credentialsPath);
        if (!resource.exists()) {
            return false;
        }
        // Local dev: explicit service account JSON on disk (gitignored).
        try (InputStream is = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info(">>> Firebase Admin SDK initialized with credentials file: {}", credentialsPath);
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK from '{}': {}", credentialsPath, e.getMessage());
            return false;
        }
    }

    private boolean tryApplicationDefaultCredentials() {
        try {
            // Cloud Run / GCP: use the service's attached IAM identity, no JSON file needed.
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            FirebaseApp.initializeApp(options);
            log.info(">>> Firebase Admin SDK initialized via Application Default Credentials.");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProdOrStage() {
        return environment.acceptsProfiles(Profiles.of("prod", "stage"));
    }
}
