package com.narturebelle.payments.care;

import com.narturebelle.payments.model.Role;

import java.util.Set;

/** What the Care API knows about a signed-in Firebase user. */
public record CallerProfile(Long userId, Set<Role> roles) {
}
