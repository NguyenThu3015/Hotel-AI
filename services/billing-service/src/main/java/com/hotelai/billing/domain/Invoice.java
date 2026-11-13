package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    private UUID id;
    @Column(name = "stay_id", nullable = false, updatable = false)
    private UUID stayId;
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "reservation_reference", nullable = false, length = 40, updatable = false)
    private String reservationReference;
    @Column(name = "invoice_number", unique = true, length = 40)
    private String invoiceNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;
    @Column(name = "buyer_name", nullable = false, length = 200)
    private String buyerName;
    @Column(name = "buyer_email", nullable = false, length = 320)
    private String buyerEmail;
    @Column(name = "buyer_address", nullable = false, length = 500)
    private String buyerAddress;
    @Column(name = "buyer_tax_code", length = 20)
    private String buyerTaxCode;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;
    @Column(name = "folio_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal folioAmount;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;
    @Column(name = "draft_idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String draftIdempotencyKey;
    @Column(name = "draft_request_hash", nullable = false, length = 64, updatable = false)
    private String draftRequestHash;
    @Column(name = "issue_idempotency_key", unique = true, length = 160)
    private String issueIdempotencyKey;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "issued_by")
    private UUID issuedBy;
    @Column(name = "issued_at")
    private Instant issuedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Version
    private long version;

    protected Invoice() {}

    public Invoice(
            InvoiceSourceSnapshot source,
            Buyer buyer,
            String idempotencyKey,
            String requestHash,
            UUID actorId) {
        validateBuyer(buyer);
        this.id = UUID.randomUUID();
        this.stayId = source.stayId();
        this.reservationId = source.reservationId();
        this.customerId = source.customerId();
        this.reservationReference = source.reservationReference();
        this.status = InvoiceStatus.DRAFT;
        this.buyerName = buyer.name().trim();
        this.buyerEmail = buyer.email().trim().toLowerCase(Locale.ROOT);
        this.buyerAddress = buyer.address().trim();
        this.buyerTaxCode = clean(buyer.taxCode());
        this.currency = source.currency();
        this.subtotal = source.subtotal();
        this.discountAmount = source.discountAmount();
        this.taxAmount = source.taxAmount();
        this.folioAmount = source.folioAmount();
        this.totalAmount = source.totalAmount();
        this.paidAmount = source.paidAmount();
        this.balance = source.balance();
        this.sourceHash = source.sourceHash();
        this.draftIdempotencyKey = idempotencyKey;
        this.draftRequestHash = requestHash;
        this.createdBy = actorId;
        this.createdAt = Instant.now();
    }

    public void issue(
            String number, String idempotencyKey, UUID actorId, Instant issuedAt) {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only a draft invoice can be issued");
        }
        if (balance.signum() != 0) {
            throw new IllegalStateException("Invoice balance must be zero before issue");
        }
        this.invoiceNumber = Objects.requireNonNull(number);
        this.issueIdempotencyKey = idempotencyKey;
        this.issuedBy = actorId;
        this.issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        this.status = InvoiceStatus.ISSUED;
    }

    public void markCancelled(Instant when) {
        if (status != InvoiceStatus.ISSUED) {
            throw new IllegalStateException("Only an issued invoice can be cancelled");
        }
        status = InvoiceStatus.CANCELLED;
        cancelledAt = when == null ? Instant.now() : when;
    }

    public boolean replayDraft(String hash) {
        if (!draftRequestHash.equals(hash)) {
            throw new IllegalStateException(
                    "Idempotency-Key was already used for a different invoice draft");
        }
        return true;
    }

    public boolean replayIssue(String key) {
        return issueIdempotencyKey != null && issueIdempotencyKey.equals(key);
    }

    private static void validateBuyer(Buyer buyer) {
        if (buyer == null
                || buyer.name() == null
                || buyer.name().isBlank()
                || buyer.email() == null
                || !buyer.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || buyer.address() == null
                || buyer.address().isBlank()) {
            throw new IllegalArgumentException(
                    "Buyer name, valid email and address are required");
        }
        if (buyer.taxCode() != null
                && !buyer.taxCode().isBlank()
                && !buyer.taxCode().matches("\\d{10}(-\\d{3})?")) {
            throw new IllegalArgumentException(
                    "Buyer tax code must contain 10 digits or 10 digits plus a 3-digit branch");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getStayId() { return stayId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReservationReference() { return reservationReference; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public InvoiceStatus getStatus() { return status; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerEmail() { return buyerEmail; }
    public String getBuyerAddress() { return buyerAddress; }
    public String getBuyerTaxCode() { return buyerTaxCode; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getFolioAmount() { return folioAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getBalance() { return balance; }
    public String getSourceHash() { return sourceHash; }
    public String getDraftRequestHash() { return draftRequestHash; }
    public String getIssueIdempotencyKey() { return issueIdempotencyKey; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getIssuedBy() { return issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public long getVersion() { return version; }

    public record Buyer(String name, String email, String address, String taxCode) {}
}
