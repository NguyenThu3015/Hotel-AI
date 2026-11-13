package com.hotelai.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_type_price_snapshots")
public class RoomTypePriceSnapshot {
    @Id
    @Column(name = "room_type_id")
    private UUID roomTypeId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", insertable = false, updatable = false)
    private RoomType roomType;
    @Column(name = "nightly_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal nightlyPrice;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;
    @Column(name = "service_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceFee;
    @Column(name = "pricing_version", nullable = false)
    private long pricingVersion;
    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomTypePriceSnapshot() {}

    public RoomTypePriceSnapshot(
            UUID roomTypeId, BigDecimal nightlyPrice, String currency, BigDecimal taxRate,
            BigDecimal serviceFee, long pricingVersion, Instant effectiveAt) {
        this.roomTypeId = roomTypeId;
        update(nightlyPrice, currency, taxRate, serviceFee, pricingVersion, effectiveAt);
    }

    public void update(
            BigDecimal nightlyPrice, String currency, BigDecimal taxRate, BigDecimal serviceFee,
            long pricingVersion, Instant effectiveAt) {
        if (pricingVersion < this.pricingVersion) return;
        this.nightlyPrice = nightlyPrice;
        this.currency = currency;
        this.taxRate = taxRate;
        this.serviceFee = serviceFee;
        this.pricingVersion = pricingVersion;
        this.effectiveAt = effectiveAt;
        this.updatedAt = Instant.now();
    }

    public UUID getRoomTypeId() { return roomTypeId; }
    public BigDecimal getNightlyPrice() { return nightlyPrice; }
    public String getCurrency() { return currency; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public long getPricingVersion() { return pricingVersion; }
    public Instant getEffectiveAt() { return effectiveAt; }
}
