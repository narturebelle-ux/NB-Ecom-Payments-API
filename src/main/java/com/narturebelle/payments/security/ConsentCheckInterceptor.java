package com.narturebelle.payments.security;

import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.exception.ConsentRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class ConsentCheckInterceptor implements HandlerInterceptor {

    private final AuthorizationService authorizationService;
    private final CareDirectory careDirectory;

    public ConsentCheckInterceptor(AuthorizationService authorizationService, CareDirectory careDirectory) {
        this.authorizationService = authorizationService;
        this.careDirectory = careDirectory;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(RequireConsent.class) == null) {
            return true;
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        List<String> pending = careDirectory.pendingConsents(principal.userId());
        if (!pending.isEmpty()) {
            throw new ConsentRequiredException(pending);
        }
        return true;
    }
}
