package com.hotelai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_payment_projections")
public class ReservationPaymentProjection {
    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(length = 30)
    private String reference;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_required", nullable = false, precision = 19, scale = 2)
    private BigDecimal depositRequired;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "stay_total_amount", precision = 19, scale = 2)
    private BigDecimal stayTotalAmount;

    @Column(name = "stay_paid_amount", precision = 19, scale = 2)
    private BigDecimal stayPaidAmount;

    @Column(name = "stay_updated_at")
    private Instant stayUpdatedAt;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    protected ReservationPaymentProjection() {}

    public ReservationPaymentProjection(
            UUID reservationId,
            UUID customerId,
            String contactEmail,
            String reference,
            String status,
            String currency,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            long sourceVersion,
            Instant sourceUpdatedAt) {
        this.reservationId = reservationId;
        synchronize(customerId, contactEmail, reference, status, currency, totalAmount, depositRequired, paidAmount, sourceVersion, sourceUpdatedAt);
    }

    public boolean synchronize(
            UUID customerId,
            String contactEmail,
            String reference,
            String status,
            String currency,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            long sourceVersion,
            Instant sourceUpdatedAt) {
        Instant eventTime = sourceUpdatedAt == null ? Instant.now() : sourceUpdatedAt;
        if (sourceVersion < this.sourceVersion
                || (sourceVersion == this.sourceVersion
                    && this.sourceUpdatedAt != null
                    && !eventTime.isAfter(this.sourceUpdatedAt))) {
            return false;
        }
        this.customerId = customerId;
        if (contactEmail == null || !contactEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Reservation contact email is invalid");
        }
        this.contactEmail = contactEmail.trim().toLowerCase(java.util.Locale.ROOT);
        this.reference = reference;
        this.status = status;
        this.currency = currency;
        this.totalAmount = money(totalAmount, "totalAmount");
        this.depositRequired = money(depositRequired, "depositRequired");
        this.paidAmount = money(paidAmount, "paidAmount");
        if (this.depositRequired.compareTo(this.totalAmount) > 0) {
            throw new IllegalArgumentException("Reservation deposit exceeds total amount");
        }
        this.sourceVersion = sourceVersion;
        this.sourceUpdatedAt = eventTime;
        return true;
    }

    private static BigDecimal money(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + " must be non-negative");
        return value.setScale(2);
    }

    public boolean synchronizeStay(
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            Instant occurredAt) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (stayUpdatedAt != null && !eventTime.isAfter(stayUpdatedAt)) return false;
        if (!this.currency.equals(currency)) {
            throw new IllegalArgumentException("Stay folio currency does not match reservation");
        }
        stayTotalAmount = money(chargesTotal, "chargesTotal");
        stayPaidAmount = money(paidTotal, "paidTotal");
        stayUpdatedAt = eventTime;
        return true;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getContactEmail() { return contactEmail; }
    public String getReference() { return reference; }
    public String getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotalAmount() {
        return stayTotalAmount == null ? totalAmount : stayTotalAmount;
    }
    public BigDecimal getDepositRequired() { return depositRequired; }
    public BigDecimal getPaidAmount() {
        return stayPaidAmount == null ? paidAmount : stayPaidAmount;
    }
    public long getSourceVersion() { return sourceVersion; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
}
