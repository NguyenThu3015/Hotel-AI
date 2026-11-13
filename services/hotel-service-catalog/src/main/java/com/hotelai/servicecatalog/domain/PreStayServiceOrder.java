package com.hotelai.servicecatalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A service requested before the guest has checked in. */
@Entity
@Table(name = "pre_stay_service_orders")
public class PreStayServiceOrder {
    @Id
    private UUID id;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;
    @Column(name = "service_name", nullable = false, length = 160)
    private String serviceName;
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PreStayServiceOrderStatus status;
    @Column(length = 500)
    private String note;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected PreStayServiceOrder() {}

    public PreStayServiceOrder(
            UUID reservationId,
            UUID customerId,
            HotelServiceItem service,
            ServicePriceVersion price,
            BigDecimal quantity,
            Instant scheduledAt,
            String note,
            UUID createdBy) {
        if (reservationId == null || customerId == null || service == null || price == null) {
            throw new IllegalArgumentException("Reservation, customer, service and price are required");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.id = UUID.randomUUID();
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.serviceId = service.getId();
        this.serviceCode = service.getCode();
        this.serviceName = service.getName();
        this.quantity = quantity.setScale(3);
        this.unitPrice = price.getUnitPrice();
        this.taxRate = price.getTaxRate();
        this.currency = price.getCurrency();
        this.scheduledAt = scheduledAt;
        this.status = PreStayServiceOrderStatus.REQUESTED;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void confirm() {
        if (status != PreStayServiceOrderStatus.REQUESTED) {
            throw new IllegalStateException("Only requested service orders can be confirmed");
        }
        status = PreStayServiceOrderStatus.CONFIRMED;
        updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (status == PreStayServiceOrderStatus.FULFILLED) {
            throw new IllegalStateException("A fulfilled service order cannot be cancelled");
        }
        if (status == PreStayServiceOrderStatus.CANCELLED) return;
        status = PreStayServiceOrderStatus.CANCELLED;
        note = reason == null || reason.isBlank() ? note : reason.trim();
        updatedAt = Instant.now();
    }

    public void fulfill() {
        if (status != PreStayServiceOrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed service orders can be fulfilled");
        }
        status = PreStayServiceOrderStatus.FULFILLED;
        updatedAt = Instant.now();
    }

    public BigDecimal grossAmount() {
        return unitPrice.multiply(quantity).multiply(BigDecimal.ONE.add(taxRate))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceCode() { return serviceCode; }
    public String getServiceName() { return serviceName; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public String getCurrency() { return currency; }
    public Instant getScheduledAt() { return scheduledAt; }
    public PreStayServiceOrderStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
