package com.hotelai.inventory.domain;

import com.hotelai.inventory.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "housekeeping_tasks")
public class HousekeepingTask {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Column(name = "assigned_to")
    private UUID assignedTo;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 20)
    private String priority;
    @Column(length = 1000)
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Version private long version;

    protected HousekeepingTask() {}

    public HousekeepingTask(Room room, String priority, String note) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.priority = priority;
        this.note = note;
        this.status = "OPEN";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void assign(UUID staffId) {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw ApiException.conflict("housekeeping-task-closed", "Công việc đã kết thúc.");
        }
        assignedTo = staffId;
        updatedAt = Instant.now();
    }

    public void changeStatus(String next) {
        boolean valid = switch (status) {
            case "OPEN" -> "IN_PROGRESS".equals(next) || "CANCELLED".equals(next);
            case "IN_PROGRESS" -> "COMPLETED".equals(next) || "CANCELLED".equals(next);
            default -> false;
        };
        if (!valid) {
            throw ApiException.conflict(
                    "invalid-housekeeping-transition",
                    "Không thể chuyển công việc từ " + status + " sang " + next + ".");
        }
        status = next;
        updatedAt = Instant.now();
        if ("COMPLETED".equals(next)) completedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public UUID getAssignedTo() { return assignedTo; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
}
