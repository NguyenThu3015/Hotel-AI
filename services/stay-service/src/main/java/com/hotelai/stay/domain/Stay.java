package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stays")
public class Stay {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID reservationId;
    @Column(nullable = false, updatable = false)
    private UUID customerId;
    @Column(nullable = false, length = 40, updatable = false)
    private String reservationReference;
    @Column(nullable = false, length = 200, updatable = false)
    private String contactName;
    @Column(nullable = false, length = 320, updatable = false)
    private String contactEmail;
    @Column(nullable = false, length = 40, updatable = false)
    private String contactPhone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StayStatus status;
    @Column(nullable = false)
    private LocalDate scheduledCheckIn;
    @Column(nullable = false)
    private LocalDate scheduledCheckOut;
    private Instant actualCheckIn;
    private Instant actualCheckOut;
    @Column(nullable = false)
    private int adults;
    @Column(nullable = false)
    private int children;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal chargesTotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidTotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingDepositAtCheckIn;
    @Column(nullable = false, length = 64, updatable = false)
    private String checkInRequestHash;
    @Column(nullable = false, updatable = false)
    private UUID createdBy;
    private UUID checkoutOverrideApprovedBy;
    @Column(length = 500)
    private String checkoutOverrideReason;
    @Column(length = 500)
    private String lastSagaError;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Stay() {}

    public static Stay pendingCheckIn(
            ReservationStayProjection reservation,
            UUID actorId,
            String requestHash,
            boolean outstandingDepositAcknowledged) {
        if (!"CONFIRMED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Reservation must be CONFIRMED before check-in");
        }
        BigDecimal outstanding = reservation.getDepositRequired()
                .subtract(reservation.getPaidAmount())
                .max(BigDecimal.ZERO)
                .setScale(2);
        if (outstanding.signum() > 0 && !outstandingDepositAcknowledged) {
            throw new IllegalStateException("Outstanding deposit must be acknowledged before check-in");
        }
        Stay value = new Stay();
        value.id = UUID.randomUUID();
        value.reservationId = reservation.getReservationId();
        value.customerId = reservation.getCustomerId();
        value.reservationReference = reservation.getReference();
        value.contactName = reservation.getContactName();
        value.contactEmail = reservation.getContactEmail();
        value.contactPhone = reservation.getContactPhone();
        value.status = StayStatus.CHECK_IN_PENDING;
        value.scheduledCheckIn = reservation.getCheckIn();
        value.scheduledCheckOut = reservation.getCheckOut();
        value.adults = reservation.getAdults();
        value.children = reservation.getChildren();
        value.currency = reservation.getCurrency();
        value.chargesTotal = reservation.getTotalAmount().setScale(2);
        value.paidTotal = reservation.getPaidAmount().setScale(2);
        value.outstandingDepositAtCheckIn = outstanding;
        value.checkInRequestHash = Objects.requireNonNull(requestHash);
        value.createdBy = Objects.requireNonNull(actorId);
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void open(Instant checkedInAt) {
        require(StayStatus.CHECK_IN_PENDING);
        status = StayStatus.OPEN;
        actualCheckIn = checkedInAt == null ? Instant.now() : checkedInAt;
        lastSagaError = null;
        touch();
    }

    public boolean synchronizePaid(BigDecimal paid) {
        if (paid == null || paid.signum() < 0) return false;
        BigDecimal normalized = paid.setScale(2);
        if (normalized.compareTo(paidTotal) == 0) return false;
        paidTotal = normalized;
        touch();
        return true;
    }

    public void addCharge(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Charge amount is required");
        BigDecimal next = chargesTotal.add(amount).setScale(2);
        if (next.signum() < 0) throw new IllegalStateException("Stay charges cannot become negative");
        chargesTotal = next;
        touch();
    }

    public void extendTo(LocalDate checkoutDate, BigDecimal priceDelta) {
        require(StayStatus.OPEN);
        if (checkoutDate == null || !checkoutDate.isAfter(scheduledCheckOut)) {
            throw new IllegalArgumentException("New checkout date must be after the current checkout date");
        }
        scheduledCheckOut = checkoutDate;
        addCharge(priceDelta);
    }

    public void markCheckoutPaymentPending() {
        if (status != StayStatus.OPEN && status != StayStatus.CHECKOUT_PAYMENT_PENDING) {
            throw new IllegalStateException("Stay cannot wait for checkout payment from " + status);
        }
        status = StayStatus.CHECKOUT_PAYMENT_PENDING;
        touch();
    }

    public void beginCheckout(UUID overrideApprovedBy, String overrideReason) {
        if (status != StayStatus.OPEN && status != StayStatus.CHECKOUT_PAYMENT_PENDING) {
            throw new IllegalStateException("Stay cannot check out from " + status);
        }
        if (balance().signum() != 0 && overrideApprovedBy == null) {
            throw new IllegalStateException("Outstanding balance must be settled or approved");
        }
        checkoutOverrideApprovedBy = overrideApprovedBy;
        checkoutOverrideReason = clean(overrideReason);
        status = StayStatus.CHECKOUT_PENDING;
        lastSagaError = null;
        touch();
    }

    public void close(Instant checkedOutAt) {
        require(StayStatus.CHECKOUT_PENDING);
        status = StayStatus.CLOSED;
        actualCheckOut = checkedOutAt == null ? Instant.now() : checkedOutAt;
        lastSagaError = null;
        touch();
    }

    public void sagaFailed(String detail) {
        lastSagaError = truncate(detail, 500);
        touch();
    }

    public void clearSagaError() {
        if (lastSagaError == null) return;
        lastSagaError = null;
        touch();
    }

    public BigDecimal balance() {
        return chargesTotal.subtract(paidTotal).setScale(2);
    }

    private void require(StayStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected stay status " + expected + " but was " + status);
        }
    }

    private void touch() { updatedAt = Instant.now(); }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        String clean = value == null ? "Unknown saga failure" : value.trim();
        return clean.substring(0, Math.min(max, clean.length()));
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReservationReference() { return reservationReference; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public StayStatus getStatus() { return status; }
    public LocalDate getScheduledCheckIn() { return scheduledCheckIn; }
    public LocalDate getScheduledCheckOut() { return scheduledCheckOut; }
    public Instant getActualCheckIn() { return actualCheckIn; }
    public Instant getActualCheckOut() { return actualCheckOut; }
    public int getAdults() { return adults; }
    public int getChildren() { return children; }
    public String getCurrency() { return currency; }
    public BigDecimal getChargesTotal() { return chargesTotal; }
    public BigDecimal getPaidTotal() { return paidTotal; }
    public BigDecimal getOutstandingDepositAtCheckIn() { return outstandingDepositAtCheckIn; }
    public String getCheckInRequestHash() { return checkInRequestHash; }
    public UUID getCheckoutOverrideApprovedBy() { return checkoutOverrideApprovedBy; }
    public String getCheckoutOverrideReason() { return checkoutOverrideReason; }
    public String getLastSagaError() { return lastSagaError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
