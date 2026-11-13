package com.hotelai.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_stay_folio_projections")
class PendingStayFolioProjection {
    @Id
    private UUID reservationId;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal chargesTotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidTotal;
    @Column(nullable = false)
    private Instant sourceUpdatedAt;

    protected PendingStayFolioProjection() {}

    PendingStayFolioProjection(
            UUID reservationId,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            Instant sourceUpdatedAt) {
        this.reservationId = reservationId;
        synchronize(currency, chargesTotal, paidTotal, sourceUpdatedAt);
    }

    boolean synchronize(
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            Instant occurredAt) {
        Instant eventTime = occurredAt == null ? Instant.now() : occurredAt;
        if (sourceUpdatedAt != null && !eventTime.isAfter(sourceUpdatedAt)) return false;
        this.currency = currency;
        this.chargesTotal = chargesTotal.setScale(2);
        this.paidTotal = paidTotal.setScale(2);
        this.sourceUpdatedAt = eventTime;
        return true;
    }

    String getCurrency() { return currency; }
    BigDecimal getChargesTotal() { return chargesTotal; }
    BigDecimal getPaidTotal() { return paidTotal; }
    Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
}
