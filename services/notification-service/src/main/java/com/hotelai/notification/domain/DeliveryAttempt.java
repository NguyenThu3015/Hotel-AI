package com.hotelai.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(nullable = false)
    private boolean successful;
    @Column(name = "provider_reference", length = 300)
    private String providerReference;
    @Column(columnDefinition = "text")
    private String error;
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected DeliveryAttempt() {}

    public DeliveryAttempt(UUID jobId, int attemptNumber, boolean successful, String reference, String error) {
        this.jobId = jobId;
        this.attemptNumber = attemptNumber;
        this.successful = successful;
        this.providerReference = reference;
        this.error = error;
        this.attemptedAt = Instant.now();
    }
}
