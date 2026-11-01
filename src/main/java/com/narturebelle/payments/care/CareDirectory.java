package com.narturebelle.payments.care;

import java.util.List;
import java.util.Optional;

/** Users, roles and consent live in the Care API. How this repo reads them is still open (F4-S3). */
public interface CareDirectory {

    Optional<CallerProfile> findCaller(String firebaseUid);

    /** Consent types the user hasn't granted yet; empty means all granted. */
    List<String> pendingConsents(Long userId);
}
