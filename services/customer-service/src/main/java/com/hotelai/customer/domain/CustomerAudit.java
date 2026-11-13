package com.hotelai.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_audit_log")
public class CustomerAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "actor_identity_id")
    private UUID actorIdentityId;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(nullable = false, columnDefinition = "text")
    private String details;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CustomerAudit() {}

    public CustomerAudit(UUID customerId, UUID actorIdentityId, String action, String details) {
        this.customerId = customerId;
        this.actorIdentityId = actorIdentityId;
        this.action = action;
        this.details = details;
        this.occurredAt = Instant.now();
    }
}
