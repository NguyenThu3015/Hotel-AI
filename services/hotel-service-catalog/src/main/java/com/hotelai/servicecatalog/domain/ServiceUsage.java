package com.hotelai.servicecatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_usages")
public class ServiceUsage {
    @Id
    private UUID id;
    @Column(name = "stay_id", nullable = false, updatable = false)
    private UUID stayId;
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;
    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;
    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;
    @Column(name = "service_name", nullable = false, length = 160)
    private String serviceName;
    @Column(nullable = false, length = 30)
    private String unit;
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceUsageStatus status;
    @Column(nullable = false)
    private int revision;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "cancelled_by")
    private UUID cancelledBy;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
    @Version
    private long version;

    protected ServiceUsage() {}

    public ServiceUsage(
            StayProjection stay,
            HotelServiceItem service,
            ServicePriceVersion price,
            BigDecimal quantity,
            Instant occurredAt,
            boolean performed,
            UUID actorId) {
        this.id = UUID.randomUUID();
        this.stayId = stay.getStayId();
        this.reservationId = stay.getReservationId();
        this.serviceId = service.getId();
        this.serviceCode = service.getCode();
        this.serviceName = service.getName();
        this.unit = service.getUnit();
        this.currency = price.getCurrency();
        this.createdBy = actorId;
        this.createdAt = Instant.now();
        this.status = performed ? ServiceUsageStatus.PERFORMED : ServiceUsageStatus.PLANNED;
        this.revision = 1;
        recalculate(quantity, price, occurredAt);
    }

    public BigDecimal adjust(
            HotelServiceItem service,
            ServicePriceVersion price,
            BigDecimal newQuantity,
            Instant newOccurredAt) {
        requireMutable();
        BigDecimal previous = grossAmount;
        this.serviceCode = service.getCode();
        this.serviceName = service.getName();
        this.unit = service.getUnit();
        this.currency = price.getCurrency();
        recalculate(newQuantity, price, newOccurredAt);
        revision++;
        return grossAmount.subtract(previous).setScale(2);
    }

    public boolean markPerformed() {
        if (status == ServiceUsageStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled usage cannot be performed");
        }
        if (status == ServiceUsageStatus.PERFORMED) return false;
        status = ServiceUsageStatus.PERFORMED;
        revision++;
        updatedAt = Instant.now();
        return true;
    }

    public BigDecimal cancel(UUID actorId, String reason, boolean manager) {
        if (status == ServiceUsageStatus.CANCELLED) return BigDecimal.ZERO.setScale(2);
        if (status == ServiceUsageStatus.PERFORMED && !manager) {
            throw new IllegalStateException("Manager approval is required to cancel a performed service");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
        BigDecimal reversal = grossAmount.negate().setScale(2);
        status = ServiceUsageStatus.CANCELLED;
        cancelledBy = actorId;
        cancelledAt = Instant.now();
        cancellationReason = reason.trim();
        revision++;
        updatedAt = Instant.now();
        return reversal;
    }

    private void recalculate(
            BigDecimal newQuantity, ServicePriceVersion price, Instant newOccurredAt) {
        if (newQuantity == null
                || newQuantity.signum() <= 0
                || newQuantity.compareTo(new BigDecimal("999.999")) > 0) {
            throw new IllegalArgumentException("Quantity must be between 0 and 999.999");
        }
        if (newOccurredAt == null) throw new IllegalArgumentException("Usage time is required");
        this.quantity = newQuantity.setScale(3);
        this.unitPrice = price.getUnitPrice();
        this.taxRate = price.getTaxRate();
        this.netAmount = unitPrice.multiply(this.quantity).setScale(2, RoundingMode.HALF_UP);
        this.taxAmount = netAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        this.grossAmount = netAmount.add(taxAmount).setScale(2);
        this.occurredAt = newOccurredAt;
        this.updatedAt = Instant.now();
    }

    private void requireMutable() {
        if (status == ServiceUsageStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled service usage cannot be adjusted");
        }
    }

    public UUID getId() { return id; }
    public UUID getStayId() { return stayId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceCode() { return serviceCode; }
    public String getServiceName() { return serviceName; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public String getCurrency() { return currency; }
    public Instant getOccurredAt() { return occurredAt; }
    public ServiceUsageStatus getStatus() { return status; }
    public int getRevision() { return revision; }
    public UUID getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public long getVersion() { return version; }
}
