package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Version private long version;

    protected OutboxEvent() {}

    public OutboxEvent(
            String aggregateType, UUID aggregateId, String eventType, UUID correlationId,
            String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.schemaVersion = 1;
        this.payload = payload;
        this.occurredAt = Instant.now();
    }

    public void markPublished() {
        this.publishAttempts++;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.publishAttempts++;
        this.lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public UUID getCorrelationId() { return correlationId; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
}
