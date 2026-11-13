package com.hotelai.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_guests")
public class ReservationGuest {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_number", length = 80)
    private String documentNumber;

    @Column(length = 2)
    private String nationality;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReservationGuest() {}

    ReservationGuest(
            Reservation reservation,
            String fullName,
            String documentType,
            String documentNumber,
            String nationality,
            boolean primary) {
        this.id = UUID.randomUUID();
        this.reservation = reservation;
        this.fullName = fullName.trim();
        this.documentType = trimToNull(documentType);
        this.documentNumber = trimToNull(documentNumber);
        this.nationality = nationality == null ? null : nationality.trim().toUpperCase(java.util.Locale.ROOT);
        this.primary = primary;
        this.createdAt = Instant.now();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getNationality() { return nationality; }
    public boolean isPrimary() { return primary; }
}
