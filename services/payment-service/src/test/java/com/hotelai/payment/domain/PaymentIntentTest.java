package com.hotelai.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIntentTest {
    @Test
    void successfulProviderCallbackIsIdempotentForSameTransaction() {
        PaymentIntent payment = payment(PaymentProviderCode.SANDBOX);
        payment.startProvider("request-1", "extra", "https://checkout.example/payment");

        assertThat(payment.succeed("transaction-1", Instant.now())).isTrue();
        assertThat(payment.succeed("transaction-1", Instant.now())).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThatThrownBy(() -> payment.succeed("transaction-2", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different transaction");
    }

    @Test
    void expiredOrCancelledPaymentCannotLaterSucceed() {
        PaymentIntent payment = payment(PaymentProviderCode.SANDBOX);
        payment.startProvider("request-2", "extra", "https://checkout.example/payment");
        assertThat(payment.cancel()).isTrue();

        assertThatThrownBy(() -> payment.succeed("late-transaction", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    private static PaymentIntent payment(PaymentProviderCode provider) {
        ReservationPaymentProjection reservation = new ReservationPaymentProjection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "guest@example.com",
                "RSV-TEST",
                "PENDING_PAYMENT",
                "VND",
                new BigDecimal("1000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                1,
                Instant.now());
        return PaymentIntent.create(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                reservation.getCustomerId(),
                PaymentPurpose.DEPOSIT,
                provider,
                new BigDecimal("300000"),
                "VND",
                reservation,
                "a".repeat(64),
                Instant.now().plusSeconds(900));
    }
}
