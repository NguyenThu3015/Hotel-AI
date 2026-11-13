package com.hotelai.reservation.domain;

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
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(nullable = false, length = 80)
    private String producer;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected InboxEvent() {}

    public InboxEvent(UUID eventId, String eventType, String producer, UUID aggregateId, String correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.producer = producer;
        this.aggregateId = aggregateId;
        this.correlationId = correlationId;
        this.receivedAt = Instant.now();
    }

    public void processed() { this.processedAt = Instant.now(); }
}
