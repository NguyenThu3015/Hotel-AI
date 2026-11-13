package com.hotelai.payment.domain;

public enum RefundStatus {
    REQUESTED,
    APPROVED,
    PROCESSING,
    COMPLETED,
    REJECTED,
    FAILED;

    public boolean reservesFunds() {
        return this == REQUESTED || this == APPROVED || this == PROCESSING || this == COMPLETED;
    }
}
