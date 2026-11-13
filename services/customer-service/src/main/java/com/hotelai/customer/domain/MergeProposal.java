package com.hotelai.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_merge_proposals")
public class MergeProposal {
    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "source_customer_id", nullable = false)
    private UUID sourceCustomerId;
    @Column(name = "target_customer_id", nullable = false)
    private UUID targetCustomerId;
    @Column(nullable = false, columnDefinition = "text")
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    @Column(name = "proposed_by_identity_id", nullable = false)
    private UUID proposedByIdentityId;
    @Column(name = "reviewed_by_identity_id")
    private UUID reviewedByIdentityId;
    @Column(name = "review_comment", columnDefinition = "text")
    private String reviewComment;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Version
    private long version;

    protected MergeProposal() {}

    public MergeProposal(UUID source, UUID target, String reason, UUID proposedBy) {
        this.sourceCustomerId = source;
        this.targetCustomerId = target;
        this.reason = reason;
        this.proposedByIdentityId = proposedBy;
        this.createdAt = Instant.now();
    }

    public void decide(Status decision, UUID reviewedBy, String comment) {
        this.status = decision;
        this.reviewedByIdentityId = reviewedBy;
        this.reviewComment = comment;
        this.reviewedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSourceCustomerId() { return sourceCustomerId; }
    public UUID getTargetCustomerId() { return targetCustomerId; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public UUID getProposedByIdentityId() { return proposedByIdentityId; }
    public UUID getReviewedByIdentityId() { return reviewedByIdentityId; }
    public String getReviewComment() { return reviewComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public long getVersion() { return version; }
}
