package com.hotelai.reservation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingSnapshot(
        UUID quoteId,
        String pricingVersion,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal depositRequired,
        boolean manualConfirmationRequired,
        Instant quoteExpiresAt,
        String priceBreakdown,
        String cancellationPolicy,
        Instant freeCancellationUntil,
        BigDecimal cancellationFeeAfterCutoff) {

    public PricingSnapshot {
        currency = currency == null ? null : currency.toUpperCase(java.util.Locale.ROOT);
        discountAmount = zeroIfNull(discountAmount);
        taxAmount = zeroIfNull(taxAmount);
        depositRequired = zeroIfNull(depositRequired);
        cancellationFeeAfterCutoff = zeroIfNull(cancellationFeeAfterCutoff);
        priceBreakdown = blankJson(priceBreakdown);
        cancellationPolicy = blankJson(cancellationPolicy);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
