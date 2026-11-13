package com.hotelai.stay.domain;

import com.hotelai.stay.config.CorrelationIdFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.slf4j.MDC;

@Entity
@Table(name = "stay_outbox_events")
public class StayOutboxEvent {
    @Id
    private UUID id;
    @Column(nullable = false, length = 80, updatable = false)
    private String aggregateType;
    @Column(nullable = false, updatable = false)
    private UUID aggregateId;
    @Column(nullable = false, length = 140, updatable = false)
    private String eventType;
    @Column(nullable = false, length = 120, updatable = false)
    private String correlationId;
    @Column(nullable = false, updatable = false)
    private int schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
    private Instant publishedAt;
    @Column(nullable = false)
    private int publishAttempts;
    @Column(length = 500)
    private String lastError;
    @Version
    private long version;

    protected StayOutboxEvent() {}

    StayOutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        String correlation = MDC.get(CorrelationIdFilter.MDC_KEY);
        this.correlationId = correlation == null ? UUID.randomUUID().toString() : correlation;
        this.schemaVersion = 1;
        this.payload = payload;
        this.occurredAt = Instant.now();
    }

    public void markPublished() {
        publishedAt = Instant.now();
        publishAttempts++;
        lastError = null;
    }

    public void markFailed(String detail) {
        publishAttempts++;
        String value = detail == null ? "Unknown publish failure" : detail;
        lastError = value.substring(0, Math.min(500, value.length()));
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getCorrelationId() { return correlationId; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
}
