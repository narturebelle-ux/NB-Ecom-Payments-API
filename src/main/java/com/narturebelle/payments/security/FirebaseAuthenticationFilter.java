package com.narturebelle.payments.security;

import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.care.CareUnavailableException;
import com.narturebelle.payments.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies the Authorization: Bearer <Firebase ID token> header on every request via the injected
 * FirebaseTokenVerifier (swappable in tests) and, when present and valid, populates the
 * SecurityContext with a FirebaseUserPrincipal. Absent/invalid tokens leave the request anonymous —
 * the SecurityFilterChain's matchers decide whether that's allowed (permitAll) or rejected
 * (401 via RestAuthenticationEntryPoint). Dev-bypass behavior lives in DefaultFirebaseTokenVerifier,
 * not here, so swapping in a test/mock verifier always takes full control of authentication.
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final FirebaseTokenVerifier tokenVerifier;
    private final CareDirectory careDirectory;

    public FirebaseAuthenticationFilter(FirebaseTokenVerifier tokenVerifier, CareDirectory careDirectory) {
        this.tokenVerifier = tokenVerifier;
        this.careDirectory = careDirectory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    authenticate(token);
                } catch (CareUnavailableException e) {
                    // The token may be fine: a Care outage must not look like being signed out.
                    log.error("Care API unavailable during sign-in", e);
                    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Please try again shortly.\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            VerifiedFirebaseToken verified = tokenVerifier.verify(token);
            Optional<CallerProfile> caller = careDirectory.findCaller(verified.uid());
            Long userId = caller.map(CallerProfile::userId).orElse(null);
            Set<Role> roles = caller.map(CallerProfile::roles)
                    .filter(r -> !r.isEmpty())
                    .orElse(Set.of(Role.MOTHER));

            FirebaseUserPrincipal principal = new FirebaseUserPrincipal(
                    userId, verified.uid(), verified.email(), verified.emailVerified(),
                    verified.phone(), verified.phone() != null, roles, verified.name()
            );

            List<GrantedAuthority> authorities = new ArrayList<>();
            for (Role role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            }
            if (principal.isVerified()) {
                authorities.add(new SimpleGrantedAuthority("VERIFIED"));
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        } catch (CareUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Firebase token verification failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
