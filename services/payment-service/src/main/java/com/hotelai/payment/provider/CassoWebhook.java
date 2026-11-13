package com.hotelai.payment.provider;

import java.math.BigDecimal;
import java.time.Instant;

public record CassoWebhook(
        String eventId,
        String reference,
        String description,
        BigDecimal amount,
        Instant occurredAt,
        String accountNumber,
        String bankName,
        String bankAbbreviation,
        String virtualAccountNumber) {

    public String eventKey() {
        return eventId == null || eventId.isBlank() ? "missing-event-id" : eventId;
    }

    public String transactionReference() {
        return reference == null || reference.isBlank() ? "CASSO-" + eventKey() : reference;
    }
}
