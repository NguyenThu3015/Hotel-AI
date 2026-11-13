package com.hotelai.inventory.domain;

import com.hotelai.inventory.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {
    @Id private UUID id;
    @Column(name = "room_number", nullable = false, unique = true, length = 30)
    private String roomNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;
    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 30)
    private RoomStatus operationalStatus;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "current_stay_id")
    private UUID currentStayId;
    @Column(length = 1000)
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private long version;

    protected Room() {}

    public Room(String roomNumber, Floor floor, RoomType roomType, String note) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.operationalStatus = RoomStatus.AVAILABLE;
        this.active = true;
        update(roomNumber, floor, roomType, note, true);
    }

    public void update(String roomNumber, Floor floor, RoomType roomType, String note, boolean active) {
        this.roomNumber = roomNumber.trim().toUpperCase(Locale.ROOT);
        this.floor = floor;
        this.roomType = roomType;
        this.note = clean(note);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public boolean transitionTo(RoomStatus next) {
        if (!operationalStatus.canTransitionTo(next)) {
            throw ApiException.conflict(
                    "invalid-room-status-transition",
                    "Không thể chuyển trạng thái phòng từ " + operationalStatus + " sang " + next + ".");
        }
        if (operationalStatus == next) return false;
        this.operationalStatus = next;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean occupy(UUID stayId) {
        if (stayId == null) throw new IllegalArgumentException("stayId is required");
        if (operationalStatus == RoomStatus.OCCUPIED) {
            if (stayId.equals(currentStayId)) return false;
            throw ApiException.conflict(
                    "room-already-occupied", "Phòng đang được gán cho một hồ sơ lưu trú khác.");
        }
        transitionTo(RoomStatus.OCCUPIED);
        currentStayId = stayId;
        updatedAt = Instant.now();
        return true;
    }

    public boolean releaseFromStay(UUID stayId) {
        if (stayId == null) throw new IllegalArgumentException("stayId is required");
        if (currentStayId == null) return false;
        if (!stayId.equals(currentStayId)) {
            throw ApiException.conflict(
                    "room-stay-mismatch", "Phòng đang thuộc một hồ sơ lưu trú khác.");
        }
        transitionTo(RoomStatus.WAITING_CLEANING);
        currentStayId = null;
        updatedAt = Instant.now();
        return true;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public Floor getFloor() { return floor; }
    public RoomType getRoomType() { return roomType; }
    public RoomStatus getOperationalStatus() { return operationalStatus; }
    public boolean isActive() { return active; }
    public UUID getCurrentStayId() { return currentStayId; }
    public String getNote() { return note; }
    public long getVersion() { return version; }
}
