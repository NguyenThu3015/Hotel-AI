package com.hotelai.billing.domain;

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
@Table(name = "stay_billing_projections")
public class StayBillingProjection {
    @Id
    @Column(name = "stay_id")
    private UUID stayId;
    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "reservation_reference", nullable = false, length = 40)
    private String reservationReference;
    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;
    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;
    @Column(name = "contact_phone", nullable = false, length = 40)
    private String contactPhone;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "scheduled_check_in", nullable = false)
    private LocalDate scheduledCheckIn;
    @Column(name = "scheduled_check_out", nullable = false)
    private LocalDate scheduledCheckOut;
    @Column(name = "actual_check_in")
    private Instant actualCheckIn;
    @Column(name = "actual_check_out")
    private Instant actualCheckOut;
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

    protected StayBillingProjection() {}

    public boolean synchronize(StaySnapshot value, Instant occurredAt) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (sourceUpdatedAt != null && !eventTime.isAfter(sourceUpdatedAt)) return false;
        validate(value);
        stayId = value.stayId();
        reservationId = value.reservationId();
        customerId = value.customerId();
        reservationReference = value.reservationReference();
        contactName = value.contactName();
        contactEmail = value.contactEmail();
        contactPhone = value.contactPhone();
        status = value.status();
        scheduledCheckIn = value.scheduledCheckIn();
        scheduledCheckOut = value.scheduledCheckOut();
        actualCheckIn = value.actualCheckIn();
        actualCheckOut = value.actualCheckOut();
        roomNumbers = value.roomNumbers() == null
                ? ""
                : value.roomNumbers().stream().map(String::trim).distinct()
                    .collect(java.util.stream.Collectors.joining(","));
        currency = value.currency();
        chargesTotal = value.chargesTotal().setScale(2);
        paidTotal = value.paidTotal().setScale(2);
        balance = value.balance().setScale(2);
        sourceUpdatedAt = eventTime;
        return true;
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return stayId.toString().contains(needle)
                || reservationReference.toLowerCase(Locale.ROOT).contains(needle)
                || contactName.toLowerCase(Locale.ROOT).contains(needle)
                || roomNumbers.toLowerCase(Locale.ROOT).contains(needle);
    }

    public List<String> roomNumberList() {
        return roomNumbers.isBlank() ? List.of() : List.of(roomNumbers.split(","));
    }

    private static void validate(StaySnapshot value) {
        if (value.stayId() == null
                || value.reservationId() == null
                || value.customerId() == null
                || value.reservationReference() == null
                || value.contactName() == null
                || value.contactEmail() == null
                || value.contactPhone() == null
                || value.status() == null
                || value.scheduledCheckIn() == null
                || value.scheduledCheckOut() == null
                || value.currency() == null
                || value.chargesTotal() == null
                || value.paidTotal() == null
                || value.balance() == null) {
            throw new IllegalArgumentException("Stay billing projection is incomplete");
        }
    }

    public UUID getStayId() { return stayId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReservationReference() { return reservationReference; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getStatus() { return status; }
    public LocalDate getScheduledCheckIn() { return scheduledCheckIn; }
    public LocalDate getScheduledCheckOut() { return scheduledCheckOut; }
    public Instant getActualCheckIn() { return actualCheckIn; }
    public Instant getActualCheckOut() { return actualCheckOut; }
    public String getCurrency() { return currency; }
    public BigDecimal getChargesTotal() { return chargesTotal; }
    public BigDecimal getPaidTotal() { return paidTotal; }
    public BigDecimal getBalance() { return balance; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }

    public record StaySnapshot(
            UUID stayId,
            UUID reservationId,
            UUID customerId,
            String reservationReference,
            String contactName,
            String contactEmail,
            String contactPhone,
            String status,
            LocalDate scheduledCheckIn,
            LocalDate scheduledCheckOut,
            Instant actualCheckIn,
            Instant actualCheckOut,
            List<UUID> roomIds,
            List<String> roomNumbers,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            BigDecimal balance,
            List<FolioLineSnapshot> folioEntries) {}

    public record FolioLineSnapshot(
            UUID entryId,
            UUID sourceId,
            String type,
            String description,
            BigDecimal amount,
            String currency,
            UUID recordedBy,
            Instant recordedAt) {}
}
