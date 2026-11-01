package com.narturebelle.payments.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Append-only audit trail. No update/delete code path exists anywhere for this entity on purpose —
 * that's what makes it append-only, not a DB-level constraint.
 */
@Entity
@Table(name = "audit_events", schema = "money")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime occurredAt;

    private Long actorUserId;

    /** Comma-separated role names, e.g. "MOTHER" or "CLINIC_ADMIN,SUPER_ADMIN". Null for unauthenticated actors. */
    private String actorRoles;

    @Column(nullable = false)
    private String action;

    private String subjectType;

    private String subjectId;

    private String reason;

    private String requestId;

    /** JSON text. Never health details or document names — see PHASE_0_1_BRIEF.md. */
    @Column(columnDefinition = "text")
    private String metadataJson;

    @PrePersist
    protected void onCreate() {
        this.occurredAt = LocalDateTime.now();
    }

    public AuditEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String actorRoles) { this.actorRoles = actorRoles; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
