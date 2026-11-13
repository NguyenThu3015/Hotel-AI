package com.hotelai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "stay_review_projections")
public class StayReviewProjection {
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
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "scheduled_check_in", nullable = false)
    private LocalDate scheduledCheckIn;
    @Column(name = "scheduled_check_out", nullable = false)
    private LocalDate scheduledCheckOut;
    @Column(name = "actual_check_out")
    private Instant actualCheckOut;
    @Column(name = "room_numbers", nullable = false, length = 500)
    private String roomNumbers;
    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    protected StayReviewProjection() {}

    public boolean synchronize(StaySnapshot value, Instant occurredAt) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (sourceUpdatedAt != null && !eventTime.isAfter(sourceUpdatedAt)) return false;
        if (value.stayId() == null
                || value.reservationId() == null
                || value.customerId() == null
                || value.reservationReference() == null
                || value.contactName() == null
                || value.contactEmail() == null
                || value.status() == null
                || value.scheduledCheckIn() == null
                || value.scheduledCheckOut() == null) {
            throw new IllegalArgumentException("Stay review projection is incomplete");
        }
        stayId = value.stayId();
        reservationId = value.reservationId();
        customerId = value.customerId();
        reservationReference = value.reservationReference();
        contactName = value.contactName();
        contactEmail = value.contactEmail();
        status = value.status();
        scheduledCheckIn = value.scheduledCheckIn();
        scheduledCheckOut = value.scheduledCheckOut();
        actualCheckOut = value.actualCheckOut();
        roomNumbers = value.roomNumbers() == null
                ? ""
                : value.roomNumbers().stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(","));
        sourceUpdatedAt = eventTime;
        return true;
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return reservationReference.toLowerCase(Locale.ROOT).contains(needle)
                || contactName.toLowerCase(Locale.ROOT).contains(needle)
                || roomNumbers.toLowerCase(Locale.ROOT).contains(needle);
    }

    public List<String> roomNumberList() {
        return roomNumbers.isBlank() ? List.of() : List.of(roomNumbers.split(","));
    }

    public UUID getStayId() { return stayId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public String getReservationReference() { return reservationReference; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getStatus() { return status; }
    public LocalDate getScheduledCheckIn() { return scheduledCheckIn; }
    public LocalDate getScheduledCheckOut() { return scheduledCheckOut; }
    public Instant getActualCheckOut() { return actualCheckOut; }
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
            java.math.BigDecimal chargesTotal,
            java.math.BigDecimal paidTotal,
            java.math.BigDecimal balance,
            List<Object> folioEntries) {}
}
