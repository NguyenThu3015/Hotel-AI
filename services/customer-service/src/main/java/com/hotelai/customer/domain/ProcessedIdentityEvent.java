package com.hotelai.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_identity_events")
public class ProcessedIdentityEvent {
    @Id
    @Column(name = "event_id", length = 120)
    private String eventId;
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedIdentityEvent() {}

    public ProcessedIdentityEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
