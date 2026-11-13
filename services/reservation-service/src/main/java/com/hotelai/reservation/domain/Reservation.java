package com.hotelai.reservation.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "created_by_identity_id", nullable = false)
    private UUID createdByIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "room_type_id", nullable = false)
    private UUID roomTypeId;

    @Column(name = "inventory_hold_id", nullable = false, unique = true)
    private UUID inventoryHoldId;

    @Column(name = "inventory_hold_version", nullable = false)
    private long inventoryHoldVersion;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "room_count", nullable = false)
    private int roomCount;

    @Column(nullable = false)
    private int adults;

    @Column(nullable = false)
    private int children;

    @Column(name = "contact_name", nullable = false, length = 160)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "special_requests", length = 1000)
    private String specialRequests;

    @Column(name = "pricing_quote_id", nullable = false)
    private UUID pricingQuoteId;

    @Column(name = "pricing_version", nullable = false, length = 80)
    private String pricingVersion;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_required", nullable = false, precision = 19, scale = 2)
    private BigDecimal depositRequired;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "manual_confirmation_required", nullable = false)
    private boolean manualConfirmationRequired;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_breakdown", nullable = false, columnDefinition = "jsonb")
    private String priceBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cancellation_policy", nullable = false, columnDefinition = "jsonb")
    private String cancellationPolicy;

    @Column(name = "free_cancellation_until")
    private Instant freeCancellationUntil;

    @Column(name = "cancellation_fee_after_cutoff", nullable = false, precision = 19, scale = 2)
    private BigDecimal cancellationFeeAfterCutoff;

    @Column(name = "cancellation_fee_applied", nullable = false, precision = 19, scale = 2)
    private BigDecimal cancellationFeeApplied;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<ReservationGuest> guests = new ArrayList<>();

    protected Reservation() {}

    public static Reservation create(
            UUID id,
            String reference,
            UUID customerId,
            UUID actorId,
            ReservationChannel channel,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String contactName,
            String contactEmail,
            String contactPhone,
            String specialRequests,
            InventoryHold hold,
            PricingSnapshot price) {
        Reservation reservation = new Reservation();
        reservation.id = id;
        reservation.reference = reference;
        reservation.customerId = customerId;
        reservation.createdByIdentityId = actorId;
        reservation.channel = channel;
        reservation.roomTypeId = roomTypeId;
        reservation.checkIn = checkIn;
        reservation.checkOut = checkOut;
        reservation.roomCount = roomCount;
        reservation.adults = adults;
        reservation.children = children;
        reservation.contactName = contactName.trim();
        reservation.contactEmail = contactEmail.trim().toLowerCase(java.util.Locale.ROOT);
        reservation.contactPhone = contactPhone.trim();
        reservation.specialRequests = trimToNull(specialRequests);
        reservation.inventoryHoldId = hold.holdId();
        reservation.inventoryHoldVersion = hold.version();
        reservation.holdExpiresAt = hold.expiresAt();
        reservation.paidAmount = BigDecimal.ZERO;
        reservation.cancellationFeeApplied = BigDecimal.ZERO;
        reservation.applyPrice(price);
        reservation.status = reservation.nextPreConfirmationStatus();
        Instant now = Instant.now();
        if (reservation.status == ReservationStatus.CONFIRMED) {
            reservation.confirmedAt = now;
            reservation.holdExpiresAt = null;
        }
        reservation.createdAt = now;
        reservation.updatedAt = now;
        return reservation;
    }

    public void addGuest(
            String fullName, String documentType, String documentNumber, String nationality, boolean primary) {
        guests.add(new ReservationGuest(this, fullName, documentType, documentNumber, nationality, primary));
    }

    public ReservationStatus recordPayment(BigDecimal amount, String paymentCurrency) {
        if (status != ReservationStatus.PENDING_PAYMENT
                && status != ReservationStatus.PENDING_CONFIRMATION
                && status != ReservationStatus.CONFIRMED
                && status != ReservationStatus.CHECKED_IN
                && status != ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("Payment cannot change reservation in status " + status);
        }
        if (!currency.equalsIgnoreCase(paymentCurrency)) {
            throw new IllegalArgumentException("Payment currency does not match reservation currency");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        paidAmount = paidAmount.add(amount);
        if (status == ReservationStatus.PENDING_PAYMENT
                && paidAmount.compareTo(depositRequired) >= 0) {
            if (manualConfirmationRequired) {
                transition(ReservationStatus.PENDING_CONFIRMATION);
            } else {
                confirmNow();
            }
        }
        touch();
        return status;
    }

    public void recordRefund(BigDecimal amount, String paymentCurrency) {
        if (!currency.equalsIgnoreCase(paymentCurrency)) {
            throw new IllegalArgumentException("Refund currency does not match reservation currency");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (amount.compareTo(paidAmount) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds reservation paid amount");
        }
        paidAmount = paidAmount.subtract(amount).setScale(2);
        touch();
    }

    public void confirm() {
        if (status != ReservationStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Only a pending manual reservation can be confirmed");
        }
        confirmNow();
        touch();
    }

    public void reject(String reason) {
        if (status != ReservationStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Only a pending manual reservation can be rejected");
        }
        transition(ReservationStatus.REJECTED);
        cancellationReason = requireReason(reason);
        cancelledAt = Instant.now();
        holdExpiresAt = null;
        touch();
    }

    public BigDecimal cancel(String reason, Instant now) {
        if (status.terminal() || status == ReservationStatus.CHECKED_IN) {
            throw new IllegalStateException("Reservation cannot be cancelled in status " + status);
        }
        transition(ReservationStatus.CANCELLED);
        cancellationReason = requireReason(reason);
        cancelledAt = now;
        cancellationFeeApplied = freeCancellationUntil != null && now.isAfter(freeCancellationUntil)
                ? cancellationFeeAfterCutoff.min(totalAmount)
                : BigDecimal.ZERO;
        holdExpiresAt = null;
        touch();
        return cancellationFeeApplied;
    }

    public void expire() {
        if (!status.awaitingConfirmation()) {
            throw new IllegalStateException("Only an unconfirmed reservation hold can expire");
        }
        transition(ReservationStatus.EXPIRED);
        holdExpiresAt = null;
        touch();
    }

    public void checkIn() {
        transition(ReservationStatus.CHECKED_IN);
        touch();
    }

    public void checkOut() {
        transition(ReservationStatus.CHECKED_OUT);
        touch();
    }

    public void noShow() {
        transition(ReservationStatus.NO_SHOW);
        touch();
    }

    public void applyChange(
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String specialRequests,
            InventoryHold hold,
            PricingSnapshot price,
            boolean preserveConfirmation) {
        if (status.terminal() || status == ReservationStatus.CHECKED_IN || status == ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("Reservation cannot be changed in status " + status);
        }
        this.roomTypeId = roomTypeId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.roomCount = roomCount;
        this.adults = adults;
        this.children = children;
        this.specialRequests = trimToNull(specialRequests);
        this.inventoryHoldId = hold.holdId();
        this.inventoryHoldVersion = hold.version();
        this.holdExpiresAt = hold.expiresAt();
        applyPrice(price);
        if (preserveConfirmation || status == ReservationStatus.CONFIRMED) {
            status = ReservationStatus.CONFIRMED;
            confirmedAt = Instant.now();
            holdExpiresAt = null;
        } else {
            status = nextPreConfirmationStatus();
            if (status == ReservationStatus.CONFIRMED) {
                confirmedAt = Instant.now();
                holdExpiresAt = null;
            } else {
                confirmedAt = null;
            }
        }
        touch();
    }

    public boolean canCustomerChangeDirectly(Instant now, java.time.Duration cutoff) {
        return (status == ReservationStatus.HOLD || status == ReservationStatus.PENDING_PAYMENT)
                && now.plus(cutoff).isBefore(checkIn.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }

    private ReservationStatus nextPreConfirmationStatus() {
        if (paidAmount.compareTo(depositRequired) < 0 && depositRequired.signum() > 0) {
            return ReservationStatus.PENDING_PAYMENT;
        }
        if (manualConfirmationRequired) return ReservationStatus.PENDING_CONFIRMATION;
        return ReservationStatus.CONFIRMED;
    }

    private void applyPrice(PricingSnapshot price) {
        pricingQuoteId = price.quoteId();
        pricingVersion = price.pricingVersion();
        currency = price.currency();
        subtotal = price.subtotal();
        discountAmount = price.discountAmount();
        taxAmount = price.taxAmount();
        totalAmount = price.totalAmount();
        depositRequired = price.depositRequired();
        manualConfirmationRequired = price.manualConfirmationRequired();
        priceBreakdown = price.priceBreakdown();
        cancellationPolicy = price.cancellationPolicy();
        freeCancellationUntil = price.freeCancellationUntil();
        cancellationFeeAfterCutoff = price.cancellationFeeAfterCutoff();
    }

    private void confirmNow() {
        transition(ReservationStatus.CONFIRMED);
        confirmedAt = Instant.now();
        holdExpiresAt = null;
    }

    private void transition(ReservationStatus target) {
        boolean allowed = switch (target) {
            case PENDING_CONFIRMATION -> status == ReservationStatus.PENDING_PAYMENT;
            case CONFIRMED -> status == ReservationStatus.PENDING_CONFIRMATION || status == ReservationStatus.PENDING_PAYMENT
                    || status == ReservationStatus.HOLD || status == ReservationStatus.DRAFT;
            case CANCELLED -> !status.terminal() && status != ReservationStatus.CHECKED_IN;
            case REJECTED -> status == ReservationStatus.PENDING_CONFIRMATION;
            case EXPIRED -> status.awaitingConfirmation();
            case CHECKED_IN -> status == ReservationStatus.CONFIRMED;
            case CHECKED_OUT -> status == ReservationStatus.CHECKED_IN;
            case NO_SHOW -> status == ReservationStatus.CONFIRMED;
            default -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid reservation transition " + status + " -> " + target);
        status = target;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required");
        return reason.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public UUID getCustomerId() { return customerId; }
    public UUID getCreatedByIdentityId() { return createdByIdentityId; }
    public ReservationChannel getChannel() { return channel; }
    public ReservationStatus getStatus() { return status; }
    public UUID getRoomTypeId() { return roomTypeId; }
    public UUID getInventoryHoldId() { return inventoryHoldId; }
    public long getInventoryHoldVersion() { return inventoryHoldVersion; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public int getRoomCount() { return roomCount; }
    public int getAdults() { return adults; }
    public int getChildren() { return children; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getSpecialRequests() { return specialRequests; }
    public UUID getPricingQuoteId() { return pricingQuoteId; }
    public String getPricingVersion() { return pricingVersion; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDepositRequired() { return depositRequired; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public boolean isManualConfirmationRequired() { return manualConfirmationRequired; }
    public String getPriceBreakdown() { return priceBreakdown; }
    public String getCancellationPolicy() { return cancellationPolicy; }
    public Instant getFreeCancellationUntil() { return freeCancellationUntil; }
    public BigDecimal getCancellationFeeAfterCutoff() { return cancellationFeeAfterCutoff; }
    public BigDecimal getCancellationFeeApplied() { return cancellationFeeApplied; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<ReservationGuest> getGuests() { return Collections.unmodifiableList(guests); }
}
