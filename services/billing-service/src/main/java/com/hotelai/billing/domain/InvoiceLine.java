package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {
    @Id
    private UUID id;
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private InvoiceLineCategory category;
    @Column(name = "source_id", updatable = false)
    private UUID sourceId;
    @Column(nullable = false, length = 300, updatable = false)
    private String description;
    @Column(nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPrice;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceLine() {}

    public InvoiceLine(UUID invoiceId, InvoiceLineSpec value) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.category = value.category();
        this.sourceId = value.sourceId();
        this.description = value.description();
        this.quantity = value.quantity().setScale(3);
        this.unitPrice = value.unitPrice().setScale(2);
        this.amount = value.amount().setScale(2);
        this.displayOrder = value.displayOrder();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public InvoiceLineCategory getCategory() { return category; }
    public UUID getSourceId() { return sourceId; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public int getDisplayOrder() { return displayOrder; }
}
