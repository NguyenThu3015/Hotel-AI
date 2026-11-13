package com.hotelai.servicecatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_price_versions")
public class ServicePriceVersion {
    @Id
    private UUID id;
    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;
    @Column(name = "version_number", nullable = false, updatable = false)
    private long versionNumber;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal taxRate;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;
    @Column(name = "valid_to")
    private Instant validTo;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ServicePriceVersion() {}

    public ServicePriceVersion(
            UUID serviceId,
            long versionNumber,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            String currency,
            Instant validFrom,
            UUID createdBy) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price must be non-negative");
        }
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 1");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be an ISO-4217 code");
        }
        this.id = UUID.randomUUID();
        this.serviceId = serviceId;
        this.versionNumber = versionNumber;
        this.unitPrice = unitPrice.setScale(2);
        this.taxRate = taxRate.setScale(4);
        this.currency = currency;
        this.validFrom = validFrom == null ? Instant.now() : validFrom;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void closeAt(Instant end) {
        if (validTo != null) throw new IllegalStateException("Price version is already closed");
        if (end == null || !end.isAfter(validFrom)) {
            throw new IllegalArgumentException("New price must start after the current price");
        }
        validTo = end;
    }

    public boolean sameFinancialTerms(
            BigDecimal candidatePrice, BigDecimal candidateTaxRate, String candidateCurrency) {
        return unitPrice.compareTo(candidatePrice) == 0
                && taxRate.compareTo(candidateTaxRate) == 0
                && currency.equals(candidateCurrency);
    }

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public long getVersionNumber() { return versionNumber; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public String getCurrency() { return currency; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
}
