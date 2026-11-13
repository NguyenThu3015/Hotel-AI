package com.hotelai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_idempotency_records")
class PaymentIdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String key;

    @Column(name = "command_scope", nullable = false, length = 120, updatable = false)
    private String scope;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "resource_type", nullable = false, length = 30, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected PaymentIdempotencyRecord() {}

    PaymentIdempotencyRecord(
            String key,
            String scope,
            UUID actorId,
            String requestHash,
            String resourceType,
            UUID resourceId,
            Instant expiresAt) {
        this.key = key;
        this.scope = scope;
        this.actorId = actorId;
        this.requestHash = requestHash;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    String getRequestHash() { return requestHash; }
    String getResourceType() { return resourceType; }
    UUID getResourceId() { return resourceId; }
}
