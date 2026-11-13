package com.hotelai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_moderation_decisions")
public class ReviewModerationDecision {
    @Id
    private UUID id;
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ModerationAction action;
    @Column(nullable = false, length = 500, updatable = false)
    private String reason;
    @Column(name = "decided_by", nullable = false, updatable = false)
    private UUID decidedBy;
    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    protected ReviewModerationDecision() {}

    public ReviewModerationDecision(
            UUID reviewId,
            ModerationAction action,
            String reason,
            UUID actorId,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Moderation reason is required");
        }
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.action = action;
        this.reason = reason.trim();
        this.decidedBy = actorId;
        this.decidedAt = now == null ? Instant.now() : now;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public UUID getReviewId() { return reviewId; }
    public ModerationAction getAction() { return action; }
    public String getReason() { return reason; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getRequestHash() { return requestHash; }
}
