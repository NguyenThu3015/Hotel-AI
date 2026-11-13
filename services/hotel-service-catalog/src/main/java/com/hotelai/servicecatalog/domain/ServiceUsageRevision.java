package com.hotelai.servicecatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_usage_revisions")
public class ServiceUsageRevision {
    @Id
    private UUID id;
    @Column(name = "usage_id", nullable = false, updatable = false)
    private UUID usageId;
    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;
    @Column(nullable = false, length = 30, updatable = false)
    private String action;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal taxRate;
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal grossAmount;
    @Column(name = "delta_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal deltaAmount;
    @Column(name = "usage_status", nullable = false, length = 20, updatable = false)
    private String usageStatus;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(length = 500, updatable = false)
    private String reason;
    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ServiceUsageRevision() {}

    public ServiceUsageRevision(
            ServiceUsage usage,
            String action,
            String idempotencyKey,
            String requestHash,
            BigDecimal deltaAmount,
            String reason,
            UUID changedBy) {
        this.id = UUID.randomUUID();
        this.usageId = usage.getId();
        this.revisionNumber = usage.getRevision();
        this.action = action;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.quantity = usage.getQuantity();
        this.unitPrice = usage.getUnitPrice();
        this.taxRate = usage.getTaxRate();
        this.grossAmount = usage.getGrossAmount();
        this.deltaAmount = deltaAmount.setScale(2);
        this.usageStatus = usage.getStatus().name();
        this.occurredAt = usage.getOccurredAt();
        this.reason = clean(reason);
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getUsageId() { return usageId; }
    public String getRequestHash() { return requestHash; }
    public BigDecimal getDeltaAmount() { return deltaAmount; }
}
