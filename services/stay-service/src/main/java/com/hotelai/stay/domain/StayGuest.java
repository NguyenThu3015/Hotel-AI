package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stay_guests")
public class StayGuest {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID stayId;
    @Column(nullable = false, length = 180)
    private String fullName;
    @Column(nullable = false)
    private LocalDate dateOfBirth;
    @Column(nullable = false, length = 80)
    private String nationality;
    @Column(nullable = false, length = 30)
    private String documentType;
    @Column(nullable = false, columnDefinition = "text")
    private String documentNumberCiphertext;
    @Column(nullable = false, length = 8)
    private String documentNumberLast4;
    @Column(nullable = false)
    private boolean primaryGuest;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected StayGuest() {}

    public StayGuest(
            UUID stayId,
            String fullName,
            LocalDate dateOfBirth,
            String nationality,
            String documentType,
            String encryptedDocument,
            String documentLast4,
            boolean primaryGuest) {
        this.id = UUID.randomUUID();
        this.stayId = stayId;
        this.fullName = fullName.trim();
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality.trim();
        this.documentType = documentType.trim().toUpperCase(java.util.Locale.ROOT);
        this.documentNumberCiphertext = encryptedDocument;
        this.documentNumberLast4 = documentLast4;
        this.primaryGuest = primaryGuest;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getStayId() { return stayId; }
    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getNationality() { return nationality; }
    public String getDocumentType() { return documentType; }
    public String getDocumentNumberLast4() { return documentNumberLast4; }
    public boolean isPrimaryGuest() { return primaryGuest; }
}
