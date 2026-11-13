package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_projections")
public class RoomProjection {
    @Id
    private UUID id;
    @Column(nullable = false, length = 30)
    private String roomNumber;
    @Column(nullable = false)
    private UUID roomTypeId;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false)
    private boolean active;
    private UUID currentStayId;
    @Column(nullable = false)
    private long sourceVersion;
    @Column(nullable = false)
    private Instant updatedAt;

    protected RoomProjection() {}

    public boolean synchronize(
            UUID id, String roomNumber, UUID roomTypeId, String status, boolean active,
            UUID currentStayId, long sourceVersion, Instant occurredAt) {
        if (this.id != null && sourceVersion < this.sourceVersion) return false;
        this.id = id;
        this.roomNumber = roomNumber;
        this.roomTypeId = roomTypeId;
        this.status = status;
        this.active = active;
        this.currentStayId = currentStayId;
        this.sourceVersion = sourceVersion;
        this.updatedAt = occurredAt == null ? Instant.now() : occurredAt;
        return true;
    }

    public boolean readyFor(UUID stayId) {
        return active
                && (("AVAILABLE".equals(status) || "RESERVED".equals(status))
                    || ("OCCUPIED".equals(status) && stayId != null && stayId.equals(currentStayId)));
    }

    public UUID getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public UUID getRoomTypeId() { return roomTypeId; }
    public String getStatus() { return status; }
    public boolean isActive() { return active; }
    public UUID getCurrentStayId() { return currentStayId; }
    public long getSourceVersion() { return sourceVersion; }
}
