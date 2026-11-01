package com.narturebelle.payments.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the real yml files with no environment variables behind them, so nothing can resolve by accident. */
class DeploymentSettingsTest {

    private static final Map<String, String> REQUIRED = Map.of(
            "spring.datasource.url", "DB_URL",
            "spring.datasource.username", "DB_USERNAME",
            "spring.datasource.password", "DB_PASSWORD",
            "firebase.service-account-base64", "FIREBASE_SERVICE_ACCOUNT_BASE64",
            "payments.mock.webhook-secret", "MOCK_PAYMENT_WEBHOOK_SECRET",
            "care.base-url", "CARE_API_URL",
            "internal.api-token", "INTERNAL_API_TOKEN");

    @ParameterizedTest
    @ValueSource(strings = {"prod", "stage"})
    void everyRequiredSetting_hasNoDefault(String profile) throws IOException {
        Environment environment = environmentFor(profile);
        REQUIRED.forEach((property, variable) -> assertThrows(IllegalArgumentException.class,
                () -> environment.getProperty(property), variable + " must have no default in " + profile));
    }

    @Test
    void everyRequiredSetting_isListedInTheReadme() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        REQUIRED.values().forEach(variable -> assertTrue(readme.contains("`" + variable + "`"), variable));
        assertTrue(readme.contains("`SPRING_PROFILES_ACTIVE`"));
    }

    private static Environment environmentFor(String profile) throws IOException {
        AbstractEnvironment environment = new AbstractEnvironment() {};
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : List.of("application-" + profile + ".yml", "application.yml")) {
            for (PropertySource<?> source : loader.load(file, new ClassPathResource(file))) {
                environment.getPropertySources().addLast(source);
            }
        }
        return environment;
    }
}
