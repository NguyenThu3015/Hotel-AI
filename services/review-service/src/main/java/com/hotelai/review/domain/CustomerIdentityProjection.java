package com.hotelai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_identity_projections")
public class CustomerIdentityProjection {
    @Id
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "identity_id", nullable = false, unique = true)
    private UUID identityId;
    @Column(name = "source_version", nullable = false)
    private long sourceVersion;
    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    protected CustomerIdentityProjection() {}

    public boolean synchronize(
            UUID customerId, UUID identityId, long version, Instant occurredAt) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (version < sourceVersion
                || (version == sourceVersion
                    && sourceUpdatedAt != null
                    && !eventTime.isAfter(sourceUpdatedAt))) {
            return false;
        }
        if (customerId == null || identityId == null) {
            throw new IllegalArgumentException("Customer identity projection is incomplete");
        }
        this.customerId = customerId;
        this.identityId = identityId;
        this.sourceVersion = version;
        this.sourceUpdatedAt = eventTime;
        return true;
    }

    public UUID getCustomerId() { return customerId; }
    public UUID getIdentityId() { return identityId; }
}
