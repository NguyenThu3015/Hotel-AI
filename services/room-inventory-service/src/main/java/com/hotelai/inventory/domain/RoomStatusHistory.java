package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_status_history")
public class RoomStatusHistory {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private RoomStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private RoomStatus newStatus;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "changed_by")
    private UUID changedBy;
    @Column(nullable = false, length = 50)
    private String source;
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected RoomStatusHistory() {}

    public RoomStatusHistory(
            Room room, RoomStatus previousStatus, RoomStatus newStatus, String reason,
            UUID changedBy, String source, UUID correlationId) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason.trim();
        this.changedBy = changedBy;
        this.source = source;
        this.correlationId = correlationId;
        this.changedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public RoomStatus getPreviousStatus() { return previousStatus; }
    public RoomStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public UUID getChangedBy() { return changedBy; }
    public String getSource() { return source; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getChangedAt() { return changedAt; }
}
