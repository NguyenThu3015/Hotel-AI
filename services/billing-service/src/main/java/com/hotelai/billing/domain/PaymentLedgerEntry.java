package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_ledger_entries")
public class PaymentLedgerEntry {
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(nullable = false, length = 20)
    private String type;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(name = "provider_reference", length = 200)
    private String providerReference;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PaymentLedgerEntry() {}

    public PaymentLedgerEntry(
            UUID eventId,
            UUID paymentId,
            UUID reservationId,
            String type,
            BigDecimal amount,
            String currency,
            String provider,
            String providerReference,
            Instant occurredAt) {
        if (eventId == null
                || paymentId == null
                || reservationId == null
                || amount == null
                || amount.signum() <= 0
                || currency == null
                || provider == null) {
            throw new IllegalArgumentException("Payment ledger event is incomplete");
        }
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.type = type;
        this.amount = amount.setScale(2);
        this.currency = currency;
        this.provider = provider;
        this.providerReference = providerReference;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public BigDecimal signedAmount() {
        return "PAYMENT".equals(type) ? amount.negate() : amount;
    }

    public UUID getEventId() { return eventId; }
    public UUID getPaymentId() { return paymentId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public Instant getOccurredAt() { return occurredAt; }
}
