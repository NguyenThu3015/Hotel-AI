package com.hotelai.reservation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface InventoryGateway {
    InventoryHold acquire(HoldCommand command, String idempotencyKey);

    void confirm(UUID holdId, UUID reservationId);

    void replace(UUID previousHoldId, UUID replacementHoldId, UUID reservationId);

    void release(UUID holdId, String reason);

    record HoldCommand(
            UUID reservationId,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            Instant requestedExpiresAt) {}
}
