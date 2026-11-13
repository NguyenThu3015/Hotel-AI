package com.hotelai.reservation.domain;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationAmendment(
        UUID roomTypeId,
        LocalDate checkIn,
        LocalDate checkOut,
        int roomCount,
        int adults,
        int children,
        String specialRequests,
        String promotionCode,
        long expectedVersion) {}
