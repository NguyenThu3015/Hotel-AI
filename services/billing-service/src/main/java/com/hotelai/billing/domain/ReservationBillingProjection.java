package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservation_billing_projections")
public class ReservationBillingProjection {
    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, length = 40)
    private String reference;
    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;
    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;
    @Column(name = "contact_phone", nullable = false, length = 40)
    private String contactPhone;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;
    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;
    @Column(name = "room_count", nullable = false)
    private int roomCount;
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
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;
    @Column(name = "price_breakdown", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String priceBreakdown;
    @Column(name = "source_version", nullable = false)
    private long sourceVersion;
    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    protected ReservationBillingProjection() {}

    public boolean synchronize(
            ReservationSnapshot value, Instant occurredAt, String priceBreakdownJson) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (value.version() < sourceVersion
                || (value.version() == sourceVersion
                    && sourceUpdatedAt != null
                    && !eventTime.isAfter(sourceUpdatedAt))) {
            return false;
        }
        validate(value);
        reservationId = value.reservationId();
        customerId = value.customerId();
        reference = value.reference();
        contactName = value.contactName();
        contactEmail = value.contactEmail();
        contactPhone = value.contactPhone();
        status = value.status();
        checkIn = value.checkIn();
        checkOut = value.checkOut();
        roomCount = value.roomCount();
        currency = value.currency();
        subtotal = money(value.subtotal());
        discountAmount = money(value.discountAmount());
        taxAmount = money(value.taxAmount());
        totalAmount = money(value.totalAmount());
        paidAmount = money(value.paidAmount());
        priceBreakdown = priceBreakdownJson == null ? "{}" : priceBreakdownJson;
        sourceVersion = value.version();
        sourceUpdatedAt = eventTime;
        return true;
    }

    public BigDecimal calculatedTotal() {
        return subtotal.subtract(discountAmount).add(taxAmount).setScale(2);
    }

    private static void validate(ReservationSnapshot value) {
        if (value.reservationId() == null
                || value.customerId() == null
                || value.reference() == null
                || value.contactName() == null
                || value.contactEmail() == null
                || value.contactPhone() == null
                || value.status() == null
                || value.checkIn() == null
                || value.checkOut() == null
                || value.roomCount() < 1
                || value.currency() == null
                || value.subtotal() == null
                || value.discountAmount() == null
                || value.taxAmount() == null
                || value.totalAmount() == null
                || value.paidAmount() == null) {
            throw new IllegalArgumentException("Reservation billing projection is incomplete");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Money cannot be negative");
        return value.setScale(2);
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReference() { return reference; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getStatus() { return status; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public int getRoomCount() { return roomCount; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getPriceBreakdown() { return priceBreakdown; }
    public long getSourceVersion() { return sourceVersion; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }

    public record ReservationSnapshot(
            UUID reservationId,
            String reference,
            UUID customerId,
            String contactName,
            String contactEmail,
            String contactPhone,
            String status,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            long version) {}
}
