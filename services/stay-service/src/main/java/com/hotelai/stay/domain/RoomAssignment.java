package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_assignments")
public class RoomAssignment {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID stayId;
    @Column(nullable = false, updatable = false)
    private UUID roomId;
    @Column(nullable = false, length = 30, updatable = false)
    private String roomNumber;
    @Column(nullable = false, updatable = false)
    private UUID roomTypeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status;
    private Instant assignedAt;
    private Instant endedAt;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false, updatable = false)
    private UUID createdBy;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RoomAssignment() {}

    public static RoomAssignment planned(
            UUID stayId, RoomProjection room, UUID actorId, String reason) {
        RoomAssignment value = new RoomAssignment();
        value.id = UUID.randomUUID();
        value.stayId = stayId;
        value.roomId = room.getId();
        value.roomNumber = room.getRoomNumber();
        value.roomTypeId = room.getRoomTypeId();
        value.status = AssignmentStatus.PLANNED;
        value.reason = reason.trim();
        value.createdBy = actorId;
        value.createdAt = Instant.now();
        return value;
    }

    public void activate(Instant at) {
        if (status == AssignmentStatus.ACTIVE) return;
        if (status != AssignmentStatus.PLANNED) {
            throw new IllegalStateException("Only planned room assignments can be activated");
        }
        status = AssignmentStatus.ACTIVE;
        assignedAt = at == null ? Instant.now() : at;
    }

    public void end(Instant at) {
        if (status == AssignmentStatus.ENDED) return;
        if (status != AssignmentStatus.ACTIVE) {
            throw new IllegalStateException("Only active room assignments can be ended");
        }
        status = AssignmentStatus.ENDED;
        endedAt = at == null ? Instant.now() : at;
    }

    public UUID getId() { return id; }
    public UUID getStayId() { return stayId; }
    public UUID getRoomId() { return roomId; }
    public String getRoomNumber() { return roomNumber; }
    public UUID getRoomTypeId() { return roomTypeId; }
    public AssignmentStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getReason() { return reason; }
}
