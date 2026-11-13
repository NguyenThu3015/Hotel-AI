package com.hotelai.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservation_change_requests")
public class ReservationChangeRequest {
    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_change", nullable = false, columnDefinition = "jsonb")
    private String requestedChange;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ReservationChangeRequest() {}

    public ReservationChangeRequest(UUID reservationId, UUID requestedBy, String requestedChange) {
        this.id = UUID.randomUUID();
        this.reservationId = reservationId;
        this.requestedBy = requestedBy;
        this.requestedChange = requestedChange;
        this.status = ChangeRequestStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void approve(UUID actor) {
        decide(ChangeRequestStatus.APPROVED, actor, null);
    }

    public void reject(UUID actor, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A rejection reason is required");
        decide(ChangeRequestStatus.REJECTED, actor, reason.trim());
    }

    public void withdraw(UUID actor) {
        if (!requestedBy.equals(actor)) throw new IllegalStateException("Only the requester can withdraw this change");
        decide(ChangeRequestStatus.WITHDRAWN, actor, "Withdrawn by requester");
    }

    private void decide(ChangeRequestStatus target, UUID actor, String reason) {
        if (status != ChangeRequestStatus.PENDING) {
            throw new IllegalStateException("Change request has already been decided");
        }
        status = target;
        decidedBy = actor;
        decisionReason = reason;
        decidedAt = Instant.now();
        updatedAt = decidedAt;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getRequestedBy() { return requestedBy; }
    public ChangeRequestStatus getStatus() { return status; }
    public String getRequestedChange() { return requestedChange; }
    public String getDecisionReason() { return decisionReason; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
