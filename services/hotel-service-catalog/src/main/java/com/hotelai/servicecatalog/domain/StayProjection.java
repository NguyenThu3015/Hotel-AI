package com.hotelai.servicecatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "stay_projections")
public class StayProjection {
    @Id
    @Column(name = "stay_id")
    private UUID stayId;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "reservation_reference", nullable = false, length = 40)
    private String reservationReference;
    @Column(name = "contact_name", length = 180)
    private String contactName;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "scheduled_check_in", nullable = false)
    private LocalDate scheduledCheckIn;
    @Column(name = "scheduled_check_out", nullable = false)
    private LocalDate scheduledCheckOut;
    @Column(name = "room_numbers", nullable = false, length = 500)
    private String roomNumbers;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "charges_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal chargesTotal;
    @Column(name = "paid_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidTotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    protected StayProjection() {}

    public boolean synchronize(StaySnapshot value, Instant occurredAt) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (sourceUpdatedAt != null && !eventTime.isAfter(sourceUpdatedAt)) return false;
        if (value.stayId() == null
                || value.reservationId() == null
                || value.customerId() == null
                || value.reservationReference() == null
                || value.status() == null
                || value.scheduledCheckIn() == null
                || value.scheduledCheckOut() == null
                || value.currency() == null
                || value.chargesTotal() == null
                || value.paidTotal() == null
                || value.balance() == null) {
            throw new IllegalArgumentException("Stay event is incomplete");
        }
        this.stayId = value.stayId();
        this.reservationId = value.reservationId();
        this.customerId = value.customerId();
        this.reservationReference = value.reservationReference();
        this.contactName = clean(value.contactName());
        this.status = value.status();
        this.scheduledCheckIn = value.scheduledCheckIn();
        this.scheduledCheckOut = value.scheduledCheckOut();
        this.roomNumbers = value.roomNumbers() == null
                ? ""
                : value.roomNumbers().stream().map(String::trim).distinct()
                    .collect(java.util.stream.Collectors.joining(","));
        this.currency = value.currency();
        this.chargesTotal = value.chargesTotal().setScale(2);
        this.paidTotal = value.paidTotal().setScale(2);
        this.balance = value.balance().setScale(2);
        this.sourceUpdatedAt = eventTime;
        return true;
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return stayId.toString().contains(needle)
                || reservationReference.toLowerCase(Locale.ROOT).contains(needle)
                || (contactName != null && contactName.toLowerCase(Locale.ROOT).contains(needle))
                || roomNumbers.toLowerCase(Locale.ROOT).contains(needle);
    }

    public boolean isOpen() { return "OPEN".equals(status); }
    public List<String> roomNumberList() {
        return roomNumbers.isBlank() ? List.of() : List.of(roomNumbers.split(","));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getStayId() { return stayId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReservationReference() { return reservationReference; }
    public String getContactName() { return contactName; }
    public String getStatus() { return status; }
    public LocalDate getScheduledCheckIn() { return scheduledCheckIn; }
    public LocalDate getScheduledCheckOut() { return scheduledCheckOut; }
    public String getCurrency() { return currency; }
    public BigDecimal getChargesTotal() { return chargesTotal; }
    public BigDecimal getPaidTotal() { return paidTotal; }
    public BigDecimal getBalance() { return balance; }

    public record StaySnapshot(
            UUID stayId,
            UUID reservationId,
            UUID customerId,
            String reservationReference,
            String contactName,
            String status,
            LocalDate scheduledCheckIn,
            LocalDate scheduledCheckOut,
            List<String> roomNumbers,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            BigDecimal balance) {}
}
