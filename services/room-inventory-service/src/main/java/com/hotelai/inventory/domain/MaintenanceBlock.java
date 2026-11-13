package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "maintenance_blocks")
public class MaintenanceBlock {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false, length = 20)
    private String severity;
    @Column(columnDefinition = "text")
    private String resolution;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Version private long version;

    protected MaintenanceBlock() {}

    public MaintenanceBlock(Room room, LocalDate startDate, String reason) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.startDate = startDate;
        this.reason = reason.trim();
        this.title = "Bảo trì phòng";
        this.description = reason.trim();
        this.severity = "MEDIUM";
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public void resolve(LocalDate endDate) {
        this.endDate = endDate;
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
    }

    public void describe(
            String title,
            String description,
            String severity,
            UUID createdBy) {
        this.title = title.trim();
        this.description = description == null || description.isBlank()
                ? null : description.trim();
        this.severity = severity;
        this.createdBy = createdBy;
    }

    public void recordResolution(String value) {
        this.resolution = value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSeverity() { return severity; }
    public String getResolution() { return resolution; }
    public UUID getCreatedBy() { return createdBy; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public long getVersion() { return version; }
}
