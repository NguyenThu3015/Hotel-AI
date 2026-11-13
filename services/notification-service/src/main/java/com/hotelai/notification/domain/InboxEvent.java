package com.hotelai.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events")
public class InboxEvent {
    @Id
    @Column(name = "event_id", length = 120)
    private String eventId;
    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
    @Column(name = "job_id")
    private UUID jobId;

    protected InboxEvent() {}

    public InboxEvent(String eventId, String eventType, UUID jobId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.jobId = jobId;
        this.receivedAt = Instant.now();
    }

    public UUID getJobId() { return jobId; }
}
