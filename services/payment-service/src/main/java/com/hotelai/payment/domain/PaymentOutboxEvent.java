package com.hotelai.payment.domain;

import com.hotelai.payment.config.CorrelationIdFilter;
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
@Table(name = "payment_outbox_events")
public class PaymentOutboxEvent {
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 80, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 120, updatable = false)
    private String eventType;

    @Column(name = "correlation_id", nullable = false, length = 120, updatable = false)
    private String correlationId;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    private long version;

    protected PaymentOutboxEvent() {}

    PaymentOutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
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

    public void markFailed(String error) {
        publishAttempts++;
        String value = error == null ? "Unknown publish failure" : error;
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
