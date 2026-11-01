package com.narturebelle.payments.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Guards /internal/**: calls from the other NartureBelle services only, with the shared private token.
 * An unset token refuses everything. */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    public InternalTokenFilter(@Value("${internal.api-token:}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The decoded path Spring routes on. getRequestURI() is raw, so /%69nternal/... would slip past this check.
        String pathInfo = request.getPathInfo();
        String path = request.getServletPath() + (pathInfo == null ? "" : pathInfo);
        return !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        boolean allowed = expected.length > 0 && given != null
                && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8));
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Missing or invalid internal token.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
