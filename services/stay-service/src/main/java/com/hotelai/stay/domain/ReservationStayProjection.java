package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reservation_stay_projections")
public class ReservationStayProjection {
    @Id
    private UUID reservationId;
    @Column(nullable = false)
    private UUID customerId;
    @Column(nullable = false, length = 40)
    private String reference;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(nullable = false)
    private UUID roomTypeId;
    @Column(nullable = false)
    private LocalDate checkIn;
    @Column(nullable = false)
    private LocalDate checkOut;
    @Column(nullable = false)
    private int roomCount;
    @Column(nullable = false)
    private int adults;
    @Column(nullable = false)
    private int children;
    @Column(nullable = false, length = 200)
    private String contactName;
    @Column(nullable = false, length = 320)
    private String contactEmail;
    @Column(nullable = false, length = 40)
    private String contactPhone;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal depositRequired;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;
    @Column
    private UUID inventoryHoldId;
    @Column(nullable = false)
    private long sourceVersion;
    @Column(nullable = false)
    private Instant sourceUpdatedAt;

    protected ReservationStayProjection() {}

    public ReservationStayProjection(ReservationSnapshot snapshot, Instant occurredAt) {
        synchronize(snapshot, occurredAt);
    }

    public boolean synchronize(ReservationSnapshot value, Instant occurredAt) {
        if (reservationId != null && value.version() < sourceVersion) return false;
        reservationId = value.reservationId();
        customerId = value.customerId();
        reference = required(value.reference(), "reference");
        status = required(value.status(), "status");
        roomTypeId = value.roomTypeId();
        checkIn = value.checkIn();
        checkOut = value.checkOut();
        roomCount = value.roomCount();
        adults = value.adults();
        children = value.children();
        contactName = required(value.contactName(), "contactName");
        contactEmail = required(value.contactEmail(), "contactEmail");
        contactPhone = required(value.contactPhone(), "contactPhone");
        currency = required(value.currency(), "currency");
        totalAmount = money(value.totalAmount());
        depositRequired = money(value.depositRequired());
        paidAmount = money(value.paidAmount());
        inventoryHoldId = value.inventoryHoldId();
        sourceVersion = value.version();
        sourceUpdatedAt = occurredAt == null ? Instant.now() : occurredAt;
        return true;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Invalid money");
        return value.setScale(2);
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReference() { return reference; }
    public String getStatus() { return status; }
    public UUID getRoomTypeId() { return roomTypeId; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public int getRoomCount() { return roomCount; }
    public int getAdults() { return adults; }
    public int getChildren() { return children; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDepositRequired() { return depositRequired; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public UUID getInventoryHoldId() { return inventoryHoldId; }
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
            long version,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String currency,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            UUID inventoryHoldId) {}
}
