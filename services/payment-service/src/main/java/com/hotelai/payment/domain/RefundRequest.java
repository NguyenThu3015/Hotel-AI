package com.hotelai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_requests")
public class RefundRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "provider_refund_id", length = 160)
    private String providerRefundId;

    @Column(name = "provider_response", columnDefinition = "text")
    private String providerResponse;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected RefundRequest() {}

    public RefundRequest(UUID paymentId, BigDecimal amount, String currency, String reason, UUID requestedBy) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Refund amount must be positive");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Refund reason is required");
        this.paymentId = paymentId;
        this.amount = amount.setScale(2);
        this.currency = currency;
        this.reason = truncate(reason, 500);
        this.requestedBy = requestedBy;
        this.status = RefundStatus.REQUESTED;
        this.requestedAt = Instant.now();
        this.updatedAt = requestedAt;
    }

    public void approve(UUID actorId) {
        requireStatus(RefundStatus.REQUESTED);
        if (requestedBy.equals(actorId)) {
            throw new IllegalStateException("Refund requester cannot approve the same refund");
        }
        approvedBy = actorId;
        status = RefundStatus.APPROVED;
        approvedAt = Instant.now();
        updatedAt = approvedAt;
    }

    public void reject(UUID actorId, String reason) {
        requireStatus(RefundStatus.REQUESTED);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Rejection reason is required");
        rejectedBy = actorId;
        decisionReason = truncate(reason, 500);
        status = RefundStatus.REJECTED;
        updatedAt = Instant.now();
    }

    public void processing(String providerRefundId, String providerResponse) {
        if (status != RefundStatus.APPROVED) {
            throw new IllegalStateException("Refund must be approved before provider processing");
        }
        this.providerRefundId = providerRefundId;
        this.providerResponse = providerResponse;
        this.status = RefundStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public boolean complete(String providerRefundId, String providerResponse) {
        if (status == RefundStatus.COMPLETED) {
            if (this.providerRefundId == null || this.providerRefundId.equals(providerRefundId)) return false;
            throw new IllegalStateException("Refund already completed with a different provider reference");
        }
        if (status != RefundStatus.APPROVED && status != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Refund cannot complete from status " + status);
        }
        if (providerRefundId != null && !providerRefundId.isBlank()) this.providerRefundId = providerRefundId;
        this.providerResponse = providerResponse;
        this.status = RefundStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
        return true;
    }

    public void fail(String providerResponse) {
        if (status != RefundStatus.APPROVED && status != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Refund cannot fail from status " + status);
        }
        this.providerResponse = providerResponse;
        this.status = RefundStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(RefundStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected status " + expected + " but was " + status);
    }

    private static String truncate(String value, int max) {
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(max, trimmed.length()));
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public RefundStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public UUID getRejectedBy() { return rejectedBy; }
    public String getDecisionReason() { return decisionReason; }
    public String getProviderRefundId() { return providerRefundId; }
    public String getProviderResponse() { return providerResponse; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
