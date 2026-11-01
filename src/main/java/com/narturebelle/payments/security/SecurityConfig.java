package com.narturebelle.payments.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final InternalTokenFilter internalTokenFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter, InternalTokenFilter internalTokenFilter,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           RestAccessDeniedHandler restAccessDeniedHandler,
                           CorsConfigurationSource corsConfigurationSource) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.internalTokenFilter = internalTokenFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Service-to-service calls, guarded by InternalTokenFilter rather than a user's sign-in.
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(
                                "/health/**",
                                "/api/v1/hello",
                                "/api/v1/plans/**",
                                "/api/v1/payments/webhook"
                        ).permitAll()
                        // Admin namespace (refunds, finance view, ...). Any *_ADMIN role qualifies.
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority(
                                "ROLE_CLINIC_ADMIN", "ROLE_HOSPITAL_ADMIN", "ROLE_OPERATIONS_ADMIN",
                                "ROLE_FINANCE_ADMIN", "ROLE_SECURITY_ADMIN", "ROLE_SUPER_ADMIN"
                        )
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
