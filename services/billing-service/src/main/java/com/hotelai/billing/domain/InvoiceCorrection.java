package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_corrections")
public class InvoiceCorrection {
    @Id
    private UUID id;
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;
    @Column(name = "correction_number", nullable = false, unique = true, length = 40, updatable = false)
    private String correctionNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private InvoiceCorrectionType type;
    @Column(name = "amount_delta", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amountDelta;
    @Column(nullable = false, length = 500, updatable = false)
    private String reason;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected InvoiceCorrection() {}

    public InvoiceCorrection(
            Invoice invoice,
            String correctionNumber,
            InvoiceCorrectionType type,
            BigDecimal amountDelta,
            String reason,
            String idempotencyKey,
            String requestHash,
            UUID issuedBy,
            Instant issuedAt) {
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Draft invoice cannot be corrected");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Correction reason is required");
        }
        if (amountDelta == null || amountDelta.signum() == 0) {
            throw new IllegalArgumentException("Correction amount must be non-zero");
        }
        this.id = UUID.randomUUID();
        this.invoiceId = invoice.getId();
        this.correctionNumber = correctionNumber;
        this.type = type;
        this.amountDelta = amountDelta.setScale(2);
        this.reason = reason.trim();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt == null ? Instant.now() : issuedAt;
    }

    public UUID getId() { return id; }
    public String getCorrectionNumber() { return correctionNumber; }
    public InvoiceCorrectionType getType() { return type; }
    public BigDecimal getAmountDelta() { return amountDelta; }
    public String getReason() { return reason; }
    public String getRequestHash() { return requestHash; }
    public Instant getIssuedAt() { return issuedAt; }
}
