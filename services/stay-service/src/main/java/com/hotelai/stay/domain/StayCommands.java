package com.hotelai.stay.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StayCommands {
    private StayCommands() {}

    public record Guest(
            String fullName,
            LocalDate dateOfBirth,
            String nationality,
            String documentType,
            String documentNumber,
            boolean primaryGuest) {}

    public record CheckIn(
            UUID reservationId,
            List<UUID> roomIds,
            List<Guest> guests,
            boolean outstandingDepositAcknowledged,
            boolean earlyCheckInApproved) {}

    public record ChangeRoom(
            UUID fromRoomId,
            UUID targetRoomId,
            BigDecimal priceDelta,
            String reason) {}

    public record Extend(
            LocalDate newCheckoutDate,
            BigDecimal priceDelta,
            String reason) {}

    public record Incidental(
            String type,
            String description,
            BigDecimal amount) {}

    public record Checkout(
            List<Incidental> incidentals,
            boolean debtOverride,
            String debtOverrideReason) {}
}
