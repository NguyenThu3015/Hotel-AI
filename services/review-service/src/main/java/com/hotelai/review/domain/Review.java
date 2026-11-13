package com.hotelai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    private UUID id;
    @Column(name = "stay_id", nullable = false, unique = true, updatable = false)
    private UUID stayId;
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "reservation_reference", nullable = false, length = 40, updatable = false)
    private String reservationReference;
    @Column(name = "author_display_name", nullable = false, length = 120, updatable = false)
    private String authorDisplayName;
    @Column(name = "customer_email", nullable = false, length = 320, updatable = false)
    private String customerEmail;
    @Column(nullable = false, updatable = false)
    private int rating;
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String comment;
    @Column(name = "comment_hash", nullable = false, length = 64, updatable = false)
    private String commentHash;
    @Column(nullable = false, length = 300, updatable = false)
    private String topics;
    @Column(name = "moderation_flags", nullable = false, columnDefinition = "jsonb", updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String moderationFlags;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewStatus status;
    @Column(nullable = false, updatable = false)
    private boolean verified;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "ai_sentiment", length = 20)
    private String aiSentiment;
    @Column(name = "ai_sentiment_score", precision = 6, scale = 5)
    private BigDecimal aiSentimentScore;
    @Column(name = "ai_topics", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String aiTopics;
    @Column(name = "ai_urgency", length = 20)
    private String aiUrgency;
    @Column(name = "ai_confidence", precision = 6, scale = 5)
    private BigDecimal aiConfidence;
    @Column(name = "ai_draft_response", columnDefinition = "text")
    private String aiDraftResponse;
    @Column(name = "ai_model_version", length = 100)
    private String aiModelVersion;
    @Column(name = "ai_provider", length = 30)
    private String aiProvider;
    @Column(name = "ai_analyzed_at")
    private Instant aiAnalyzedAt;
    @Version
    private long version;

    protected Review() {}

    public Review(
            StayReviewProjection stay,
            int rating,
            String sanitizedComment,
            String commentHash,
            String topics,
            String moderationFlags,
            ReviewStatus status,
            String idempotencyKey,
            String requestHash) {
        if (!stay.isClosed()) {
            throw new IllegalStateException("Only a completed stay can be reviewed");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.id = UUID.randomUUID();
        this.stayId = stay.getStayId();
        this.reservationId = stay.getReservationId();
        this.customerId = stay.getCustomerId();
        this.reservationReference = stay.getReservationReference();
        this.authorDisplayName = initials(stay.getContactName());
        this.customerEmail = stay.getContactEmail().trim().toLowerCase(java.util.Locale.ROOT);
        this.rating = rating;
        this.comment = Objects.requireNonNull(sanitizedComment);
        this.commentHash = Objects.requireNonNull(commentHash);
        this.topics = Objects.requireNonNull(topics);
        this.moderationFlags = Objects.requireNonNull(moderationFlags);
        this.status = Objects.requireNonNull(status);
        this.verified = true;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.requestHash = Objects.requireNonNull(requestHash);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        if (status == ReviewStatus.PUBLISHED) publishedAt = createdAt;
    }

    public void moderate(ModerationAction action, Instant now) {
        ReviewStatus target = switch (action) {
            case PUBLISH -> ReviewStatus.PUBLISHED;
            case HIDE -> ReviewStatus.HIDDEN;
            case REJECT -> ReviewStatus.REJECTED;
        };
        if (status == target) {
            throw new IllegalStateException("Review already has the requested status");
        }
        status = target;
        updatedAt = now == null ? Instant.now() : now;
        if (target == ReviewStatus.PUBLISHED) publishedAt = updatedAt;
    }

    public boolean replay(String hash) {
        if (!requestHash.equals(hash)) {
            throw new IllegalStateException(
                    "Idempotency-Key was already used for a different review");
        }
        return true;
    }

    public void applyAiAnalysis(
            String sentiment,
            BigDecimal score,
            String topics,
            String urgency,
            BigDecimal confidence,
            String draftResponse,
            String modelVersion,
            String provider) {
        this.aiSentiment = Objects.requireNonNull(sentiment);
        this.aiSentimentScore = Objects.requireNonNull(score);
        this.aiTopics = Objects.requireNonNull(topics);
        this.aiUrgency = Objects.requireNonNull(urgency);
        this.aiConfidence = Objects.requireNonNull(confidence);
        this.aiDraftResponse = Objects.requireNonNull(draftResponse);
        this.aiModelVersion = Objects.requireNonNull(modelVersion);
        this.aiProvider = Objects.requireNonNull(provider);
        this.aiAnalyzedAt = Instant.now();
        this.updatedAt = aiAnalyzedAt;
    }

    private static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                if (!result.isEmpty()) result.append(' ');
                result.append(Character.toUpperCase(word.charAt(0))).append('.');
            }
        }
        return result.isEmpty() ? "Guest" : result.toString();
    }

    public UUID getId() { return id; }
    public UUID getStayId() { return stayId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReservationReference() { return reservationReference; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    String getCustomerEmail() { return customerEmail; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public String getCommentHash() { return commentHash; }
    public String getTopics() { return topics; }
    public String getModerationFlags() { return moderationFlags; }
    public ReviewStatus getStatus() { return status; }
    public boolean isVerified() { return verified; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getAiSentiment() { return aiSentiment; }
    public BigDecimal getAiSentimentScore() { return aiSentimentScore; }
    public String getAiTopics() { return aiTopics; }
    public String getAiUrgency() { return aiUrgency; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public String getAiDraftResponse() { return aiDraftResponse; }
    public String getAiModelVersion() { return aiModelVersion; }
    public String getAiProvider() { return aiProvider; }
    public Instant getAiAnalyzedAt() { return aiAnalyzedAt; }
    public long getVersion() { return version; }
}
