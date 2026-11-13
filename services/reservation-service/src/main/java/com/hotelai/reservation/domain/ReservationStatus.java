package com.hotelai.reservation.domain;

import java.util.EnumSet;

public enum ReservationStatus {
    DRAFT,
    HOLD,
    PENDING_PAYMENT,
    PENDING_CONFIRMATION,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    EXPIRED,
    REJECTED,
    CANCELLED,
    NO_SHOW;

    private static final EnumSet<ReservationStatus> TERMINAL =
            EnumSet.of(CHECKED_OUT, EXPIRED, REJECTED, CANCELLED, NO_SHOW);

    public boolean terminal() {
        return TERMINAL.contains(this);
    }

    public boolean awaitingConfirmation() {
        return this == HOLD || this == PENDING_PAYMENT || this == PENDING_CONFIRMATION;
    }
}
