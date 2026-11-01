package com.narturebelle.payments.exception;

import com.narturebelle.payments.care.CareUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleEmailNotVerified(EmailNotVerifiedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "EMAIL_NOT_VERIFIED", "message", e.getMessage()));
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserProfileNotFound(UserProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "USER_NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleConsentRequired(ConsentRequiredException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "CONSENT_REQUIRED");
        body.put("pending", e.getPending());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(CareUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleCareUnavailable(CareUnavailableException e) {
        log.error("Care API unavailable", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "SERVICE_UNAVAILABLE", "message", "Please try again shortly."));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePlanNotFound(PlanNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "PLAN_NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyKeyConflict(IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "IDEMPOTENCY_KEY_CONFLICT", "message", e.getMessage()));
    }

    @ExceptionHandler(PaymentNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handlePaymentNotAllowed(PaymentNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "PAYMENT_NOT_ALLOWED", "message", e.getMessage()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePaymentNotFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "PAYMENT_NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<Map<String, String>> handleInvalidWebhookSignature(InvalidWebhookSignatureException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "INVALID_WEBHOOK_SIGNATURE", "message", e.getMessage()));
    }

    @ExceptionHandler(RefundNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleRefundNotAllowed(RefundNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "REFUND_NOT_ALLOWED", "message", e.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

    /**
     * A required header (e.g. Idempotency-Key) is missing. Without this, Spring's
     * MissingRequestHeaderException would fall through to the 500 catch-all below instead of a 400 —
     * nothing needed a required header before Phase 2's POST /orders.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "VALIDATION_FAILED");
        body.put("message", "Missing required header: " + e.getHeaderName());
        body.put("fields", Map.of(e.getHeaderName(), "This header is required."));
        return ResponseEntity.badRequest().body(body);
    }

    /** A query/path parameter that can't be converted (e.g. ?professionalType=NOPE) -- a 400, not the 500 catch-all. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "VALIDATION_FAILED");
        body.put("message", "Invalid value for parameter: " + e.getName());
        body.put("fields", Map.of(e.getName(), "Invalid value: " + e.getValue()));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "VALIDATION_FAILED");
        body.put("message", "Missing required parameter: " + e.getParameterName());
        body.put("fields", Map.of(e.getParameterName(), "This parameter is required."));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedBody(HttpMessageNotReadableException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "VALIDATION_FAILED");
        body.put("message", "Request body is missing or malformed.");
        body.put("fields", Map.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code", "VALIDATION_FAILED");
        body.put("message", "One or more fields are invalid.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Spring Security's AccessDeniedException must NOT be swallowed by the catch-all below — it needs
     * to keep propagating to ExceptionTranslationFilter so RestAccessDeniedHandler's 403 FORBIDDEN
     * shape still fires (AuthorizationService.requireSelfOrAdmin relies on this). Since this handler
     * matches more specifically than the Exception catch-all, Spring resolves it here first; re-throwing
     * sends it back up past MVC's exception resolution exactly as if no handler had matched at all.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    /**
     * No route matched at all — a plain 404, not our 500 catch-all (more specific, so it wins).
     * Spring 6.1+ throws NoResourceFoundException for this in practice; NoHandlerFoundException is
     * kept too since its triggering condition (spring.mvc.throw-exception-if-no-handler-found) is a
     * config switch, not a Spring-version guarantee.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Void> handleNoHandlerFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_ERROR", "message", "Something went wrong. Please try again."));
    }
}
