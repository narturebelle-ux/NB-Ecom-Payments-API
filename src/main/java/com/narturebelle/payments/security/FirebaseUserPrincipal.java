package com.narturebelle.payments.security;

import com.narturebelle.payments.model.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Authentication principal built from a verified Firebase token plus our DB's User row (if any).
 * userId is null when the token is valid but the user hasn't called POST /api/v1/auth/firebase yet.
 */
public record FirebaseUserPrincipal(
        Long userId,
        String firebaseUid,
        String email,
        boolean emailVerified,
        String phone,
        boolean phoneVerified,
        Set<Role> roles,
        String name
) {
    private static final Set<Role> ADMIN_ROLES = EnumSet.of(
            Role.CLINIC_ADMIN, Role.HOSPITAL_ADMIN, Role.OPERATIONS_ADMIN,
            Role.FINANCE_ADMIN, Role.SECURITY_ADMIN, Role.SUPER_ADMIN
    );

    public boolean isVerified() {
        return emailVerified || phone != null;
    }

    /** True if the caller holds any of the *_ADMIN roles — used by the /api/v1/admin/** gate and requireSelfOrAdmin. */
    public boolean isAdmin() {
        return roles != null && roles.stream().anyMatch(ADMIN_ROLES::contains);
    }
}
