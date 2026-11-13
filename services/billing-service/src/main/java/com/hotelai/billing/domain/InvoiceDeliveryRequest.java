package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_delivery_requests")
public class InvoiceDeliveryRequest {
    @Id
    private UUID id;
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;
    @Column(nullable = false, length = 320, updatable = false)
    private String recipient;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected InvoiceDeliveryRequest() {}

    public InvoiceDeliveryRequest(
            UUID invoiceId,
            String recipient,
            String idempotencyKey,
            String requestHash,
            UUID requestedBy) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.recipient = recipient;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getRecipient() { return recipient; }
    public String getRequestHash() { return requestHash; }
}
