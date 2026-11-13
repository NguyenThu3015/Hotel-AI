package com.hotelai.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentCommands {
    private PaymentCommands() {}

    public record CreateIntent(
            UUID reservationId,
            PaymentPurpose purpose,
            PaymentProviderCode provider,
            BigDecimal amount,
            String currency) {}

    public record RecordCash(
            UUID reservationId,
            PaymentPurpose purpose,
            BigDecimal amount,
            String currency,
            String note) {}

    public record RequestRefund(UUID paymentId, BigDecimal amount, String reason) {}
}
