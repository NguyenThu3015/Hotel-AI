package com.hotelai.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String key;

    @Column(name = "command_scope", nullable = false, length = 100)
    private String scope;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(
            String key, String scope, UUID actorId, String requestHash, String resourceType, UUID resourceId) {
        this.id = UUID.randomUUID();
        this.key = key;
        this.scope = scope;
        this.actorId = actorId;
        this.requestHash = requestHash;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(java.time.Duration.ofHours(24));
    }

    public String getRequestHash() { return requestHash; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
}
