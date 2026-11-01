package com.narturebelle.payments.repository;

import com.narturebelle.payments.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** No update/delete methods — the audit log is append-only by omission, not a DB constraint. */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
