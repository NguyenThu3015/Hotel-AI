package com.hotelai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_response_revisions")
public class ReviewResponseRevision {
    @Id
    private UUID id;
    @Column(name = "response_id", nullable = false, updatable = false)
    private UUID responseId;
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;
    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;
    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    protected ReviewResponseRevision() {}

    public ReviewResponseRevision(
            ReviewResponse response,
            String idempotencyKey,
            String requestHash,
            UUID actorId) {
        this.id = UUID.randomUUID();
        this.responseId = response.getId();
        this.reviewId = response.getReviewId();
        this.revisionNumber = response.getRevisionNumber();
        this.content = response.getContent();
        this.changedBy = actorId;
        this.changedAt = response.getUpdatedAt();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public UUID getReviewId() { return reviewId; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getContent() { return content; }
    public UUID getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public String getRequestHash() { return requestHash; }
}
