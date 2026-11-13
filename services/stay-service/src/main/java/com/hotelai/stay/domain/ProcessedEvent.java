package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id
    private UUID eventId;
    @Column(nullable = false, length = 140)
    private String eventType;
    @Column(nullable = false, length = 80)
    private String producer;
    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId, String eventType, String producer) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.producer = producer;
        this.processedAt = Instant.now();
    }
}
