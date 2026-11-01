package com.narturebelle.payments.care;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fails closed while care.base-url is unset: every caller is an unknown user with no admin role, and
 * consent is never treated as granted. So nobody can order, pay or reach admin endpoints.
 */
@Component
@ConditionalOnExpression("'${care.base-url:}'.isEmpty()")
public class UnconnectedCareDirectory implements CareDirectory {

    @Override
    public Optional<CallerProfile> findCaller(String firebaseUid) {
        return Optional.empty();
    }

    @Override
    public List<String> pendingConsents(Long userId) {
        return List.of("CARE_API_NOT_CONNECTED");
    }
}
