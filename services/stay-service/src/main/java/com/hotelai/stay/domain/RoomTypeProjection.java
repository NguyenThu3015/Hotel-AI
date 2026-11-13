package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_type_projections")
public class RoomTypeProjection {
    @Id
    private UUID id;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false)
    private int maxAdults;
    @Column(nullable = false)
    private int maxChildren;
    @Column(nullable = false)
    private int maxOccupancy;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private long sourceVersion;
    @Column(nullable = false)
    private Instant updatedAt;

    protected RoomTypeProjection() {}

    public boolean synchronize(
            UUID id, String code, String name, int maxAdults, int maxChildren,
            int maxOccupancy, boolean active, long sourceVersion, Instant occurredAt) {
        if (this.id != null && sourceVersion < this.sourceVersion) return false;
        this.id = id;
        this.code = code;
        this.name = name;
        this.maxAdults = maxAdults;
        this.maxChildren = maxChildren;
        this.maxOccupancy = maxOccupancy;
        this.active = active;
        this.sourceVersion = sourceVersion;
        this.updatedAt = occurredAt == null ? Instant.now() : occurredAt;
        return true;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getMaxAdults() { return maxAdults; }
    public int getMaxChildren() { return maxChildren; }
    public int getMaxOccupancy() { return maxOccupancy; }
    public boolean isActive() { return active; }
}
