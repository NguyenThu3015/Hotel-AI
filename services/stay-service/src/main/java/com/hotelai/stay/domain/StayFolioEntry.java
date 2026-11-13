package com.hotelai.stay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stay_folio_entries")
public class StayFolioEntry {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID stayId;
    @Column(nullable = false, updatable = false)
    private UUID operationId;
    @Column(nullable = false, length = 40)
    private String entryType;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, updatable = false)
    private UUID recordedBy;
    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    protected StayFolioEntry() {}

    public StayFolioEntry(
            UUID stayId, UUID operationId, String entryType, String description,
            BigDecimal amount, String currency, UUID recordedBy) {
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Folio amount must be non-zero");
        }
        this.id = UUID.randomUUID();
        this.stayId = stayId;
        this.operationId = operationId;
        this.entryType = entryType.trim().toUpperCase(java.util.Locale.ROOT);
        this.description = description.trim();
        this.amount = amount.setScale(2);
        this.currency = currency;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOperationId() { return operationId; }
    public String getEntryType() { return entryType; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getRecordedAt() { return recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
}
