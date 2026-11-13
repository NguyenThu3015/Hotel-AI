package com.hotelai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_responses")
public class ReviewResponse {
    @Id
    private UUID id;
    @Column(name = "review_id", nullable = false, unique = true, updatable = false)
    private UUID reviewId;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "responded_by", nullable = false)
    private UUID respondedBy;
    @Column(name = "responded_at", nullable = false, updatable = false)
    private Instant respondedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @Version
    private long version;

    protected ReviewResponse() {}

    public ReviewResponse(UUID reviewId, String content, UUID actorId, Instant now) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.content = requireContent(content);
        this.respondedBy = actorId;
        this.respondedAt = now == null ? Instant.now() : now;
        this.updatedAt = respondedAt;
        this.revisionNumber = 1;
    }

    public void revise(String content, UUID actorId, Instant now) {
        this.content = requireContent(content);
        this.respondedBy = actorId;
        this.updatedAt = now == null ? Instant.now() : now;
        this.revisionNumber++;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Official response content is required");
        }
        return content.trim();
    }

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public String getContent() { return content; }
    public UUID getRespondedBy() { return respondedBy; }
    public Instant getRespondedAt() { return respondedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getRevisionNumber() { return revisionNumber; }
}
