package com.hotelai.billing.domain;

import com.hotelai.billing.domain.StayBillingProjection.FolioLineSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stay_folio_line_projections")
public class StayFolioLineProjection {
    @Id
    @Column(name = "entry_id")
    private UUID entryId;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected StayFolioLineProjection() {}

    public StayFolioLineProjection(UUID stayId, FolioLineSnapshot value) {
        if (value.entryId() == null
                || value.sourceId() == null
                || value.type() == null
                || value.description() == null
                || value.amount() == null
                || value.amount().signum() == 0
                || value.currency() == null
                || value.recordedBy() == null
                || value.recordedAt() == null) {
            throw new IllegalArgumentException("Stay folio line is incomplete");
        }
        this.entryId = value.entryId();
        this.stayId = stayId;
        this.sourceId = value.sourceId();
        this.type = value.type();
        this.description = value.description();
        this.amount = value.amount().setScale(2);
        this.currency = value.currency();
        this.recordedBy = value.recordedBy();
        this.recordedAt = value.recordedAt();
    }

    public UUID getEntryId() { return entryId; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getRecordedAt() { return recordedAt; }
}
