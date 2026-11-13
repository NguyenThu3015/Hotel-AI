package com.hotelai.payment.domain;

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

@Entity
@Table(name = "payment_intents")
public class PaymentIntent {
    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "created_by_identity_id", nullable = false, updatable = false)
    private UUID createdByIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentProviderCode provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "reservation_total_snapshot", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal reservationTotalSnapshot;

    @Column(name = "reservation_deposit_snapshot", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal reservationDepositSnapshot;

    @Column(name = "reservation_paid_snapshot", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal reservationPaidSnapshot;

    @Column(name = "reservation_version_snapshot", nullable = false, updatable = false)
    private long reservationVersionSnapshot;

    @Column(name = "reservation_snapshot_hash", nullable = false, length = 64, updatable = false)
    private String reservationSnapshotHash;

    @Column(name = "provider_order_id", nullable = false, length = 100, updatable = false)
    private String providerOrderId;

    @Column(name = "provider_request_id", length = 100)
    private String providerRequestId;

    @Column(name = "provider_transaction_id", length = 160)
    private String providerTransactionId;

    @Column(name = "provider_extra_data", columnDefinition = "text")
    private String providerExtraData;

    @Column(name = "checkout_url", columnDefinition = "text")
    private String checkoutUrl;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "staff_note", length = 500)
    private String staffNote;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentIntent() {}

    public static PaymentIntent create(
            UUID reservationId,
            UUID customerId,
            UUID actorId,
            PaymentPurpose purpose,
            PaymentProviderCode provider,
            BigDecimal amount,
            String currency,
            ReservationPaymentProjection snapshot,
            String snapshotHash,
            Instant expiresAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        PaymentIntent intent = new PaymentIntent();
        intent.id = UUID.randomUUID();
        intent.reservationId = Objects.requireNonNull(reservationId);
        intent.customerId = Objects.requireNonNull(customerId);
        intent.createdByIdentityId = Objects.requireNonNull(actorId);
        intent.purpose = Objects.requireNonNull(purpose);
        intent.provider = Objects.requireNonNull(provider);
        intent.status = PaymentStatus.CREATED;
        intent.amount = amount.setScale(2);
        intent.currency = normalizeCurrency(currency);
        intent.reservationTotalSnapshot = snapshot.getTotalAmount();
        intent.reservationDepositSnapshot = snapshot.getDepositRequired();
        intent.reservationPaidSnapshot = snapshot.getPaidAmount();
        intent.reservationVersionSnapshot = snapshot.getSourceVersion();
        intent.reservationSnapshotHash = Objects.requireNonNull(snapshotHash);
        intent.providerOrderId = "PAY-" + intent.id.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        intent.expiresAt = expiresAt;
        intent.createdAt = Instant.now();
        intent.updatedAt = intent.createdAt;
        return intent;
    }

    public void startProvider(String requestId, String extraData, String checkoutUrl) {
        requireStatus(PaymentStatus.CREATED);
        if (provider == PaymentProviderCode.CASH) {
            throw new IllegalStateException("Cash payment does not create a provider checkout");
        }
        if (requestId == null || requestId.isBlank() || checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new IllegalArgumentException("Provider checkout response is incomplete");
        }
        this.providerRequestId = requestId;
        this.providerExtraData = extraData;
        this.checkoutUrl = checkoutUrl;
        this.status = PaymentStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public void markProviderResultUnknown(String requestId, String extraData, String detail) {
        requireStatus(PaymentStatus.CREATED);
        if (provider == PaymentProviderCode.CASH) {
            throw new IllegalStateException("Cash payment cannot have an uncertain provider result");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Provider request id is required");
        }
        this.providerRequestId = requestId;
        this.providerExtraData = extraData;
        this.status = PaymentStatus.PENDING;
        this.failureCode = "PROVIDER_RESULT_UNKNOWN";
        this.failureMessage = truncate(detail, 500);
        this.updatedAt = Instant.now();
    }

    public boolean succeed(String transactionId, Instant paidAt) {
        if (status == PaymentStatus.SUCCEEDED) {
            if (Objects.equals(providerTransactionId, transactionId)) return false;
            throw new IllegalStateException("Payment has already succeeded with a different transaction");
        }
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment cannot succeed from status " + status);
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Provider transaction id is required");
        }
        status = PaymentStatus.SUCCEEDED;
        providerTransactionId = transactionId;
        completedAt = paidAt == null ? Instant.now() : paidAt;
        updatedAt = Instant.now();
        return true;
    }

    public boolean succeedVerifiedBankTransfer(String transactionId, Instant paidAt) {
        if (status != PaymentStatus.EXPIRED) return succeed(transactionId, paidAt);
        Instant effectivePaidAt = paidAt == null ? Instant.now() : paidAt;
        if (expiresAt == null || effectivePaidAt.isAfter(expiresAt)) {
            throw new IllegalStateException("Verified bank transfer was received after payment expiry");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Provider transaction id is required");
        }
        status = PaymentStatus.SUCCEEDED;
        providerTransactionId = transactionId;
        completedAt = effectivePaidAt;
        failureCode = null;
        failureMessage = null;
        updatedAt = Instant.now();
        return true;
    }

    public boolean fail(String code, String message) {
        if (status == PaymentStatus.FAILED) return false;
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment cannot fail from status " + status);
        }
        status = PaymentStatus.FAILED;
        failureCode = truncate(code, 80);
        failureMessage = truncate(message, 500);
        completedAt = Instant.now();
        updatedAt = completedAt;
        return true;
    }

    public boolean expire(Instant now) {
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) return false;
        if (expiresAt == null || expiresAt.isAfter(now)) return false;
        status = PaymentStatus.EXPIRED;
        completedAt = now;
        updatedAt = now;
        return true;
    }

    public boolean providerResultUnknown() {
        return status == PaymentStatus.PENDING
                && "PROVIDER_RESULT_UNKNOWN".equals(failureCode);
    }

    public boolean cancel() {
        if (status == PaymentStatus.CANCELLED) return false;
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment cannot be cancelled from status " + status);
        }
        status = PaymentStatus.CANCELLED;
        completedAt = Instant.now();
        updatedAt = completedAt;
        return true;
    }

    public void setStaffNote(String note) {
        this.staffNote = truncate(note, 500);
    }

    private void requireStatus(PaymentStatus required) {
        if (status != required) throw new IllegalStateException("Expected status " + required + " but was " + status);
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase ISO code");
        }
        return currency;
    }

    private static String truncate(String value, int maximum) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(maximum, trimmed.length()));
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getCreatedByIdentityId() { return createdByIdentityId; }
    public PaymentPurpose getPurpose() { return purpose; }
    public PaymentProviderCode getProvider() { return provider; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BigDecimal getReservationTotalSnapshot() { return reservationTotalSnapshot; }
    public BigDecimal getReservationDepositSnapshot() { return reservationDepositSnapshot; }
    public BigDecimal getReservationPaidSnapshot() { return reservationPaidSnapshot; }
    public long getReservationVersionSnapshot() { return reservationVersionSnapshot; }
    public String getReservationSnapshotHash() { return reservationSnapshotHash; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderRequestId() { return providerRequestId; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public String getProviderExtraData() { return providerExtraData; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getStaffNote() { return staffNote; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
