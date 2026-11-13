package com.hotelai.reservation.domain;

import java.time.LocalDate;
import java.util.UUID;

public interface PricingGateway {
    PricingSnapshot quote(QuoteCommand command);

    void synchronizePromotion(
            UUID reservationId,
            UUID quoteId,
            UUID customerId,
            boolean compensation,
            String idempotencyKey);

    void releasePromotion(UUID reservationId, String idempotencyKey);

    record QuoteCommand(
            UUID customerId,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String promotionCode,
            String channel) {}
}
