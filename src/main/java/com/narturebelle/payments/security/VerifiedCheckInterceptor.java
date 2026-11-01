package com.narturebelle.payments.security;

import com.narturebelle.payments.exception.EmailNotVerifiedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class VerifiedCheckInterceptor implements HandlerInterceptor {

    private final AuthorizationService authorizationService;

    public VerifiedCheckInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(RequireVerified.class) == null) {
            return true;
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (!principal.isVerified()) {
            throw new EmailNotVerifiedException(
                    "Your email or phone must be verified to perform this action.");
        }
        return true;
    }
}
