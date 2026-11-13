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
@Table(name = "customer_notes")
public class CustomerNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, columnDefinition = "text")
    private String note;
    @Column(name = "created_by_identity_id", nullable = false)
    private UUID createdByIdentityId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerNote() {}

    public CustomerNote(UUID customerId, String note, UUID createdByIdentityId) {
        this.customerId = customerId;
        this.note = note;
        this.createdByIdentityId = createdByIdentityId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getNote() { return note; }
    public UUID getCreatedByIdentityId() { return createdByIdentityId; }
    public Instant getCreatedAt() { return createdAt; }
}
