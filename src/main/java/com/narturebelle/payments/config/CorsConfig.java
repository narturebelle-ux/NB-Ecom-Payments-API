package com.narturebelle.payments.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    // Comma-separated list, e.g. "https://narturebelle.vercel.app,https://narturebelle.com".
    // Defaults to "*" for local dev/test; prod/stage override via CORS_ALLOWED_ORIGINS.
    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    /** Consumed by SecurityConfig's SecurityFilterChain — Spring Security owns CORS, not a separate WebMvcConfigurer. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Webhook-Signature"));
        // Lets the browser read GET /me/export's download filename cross-origin.
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
