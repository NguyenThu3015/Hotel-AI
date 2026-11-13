package com.hotelai.stay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelai.stay.domain.ReservationStayProjection.ReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StayTest {
    @Test
    void checkInTracksDepositAndCheckoutRequiresSettledBalanceOrManagerOverride() {
        ReservationStayProjection reservation = reservation(
                new BigDecimal("1000000"), new BigDecimal("300000"), new BigDecimal("200000"));
        Stay stay = Stay.pendingCheckIn(
                reservation, UUID.randomUUID(), "a".repeat(64), true);

        stay.open(Instant.parse("2026-07-17T08:00:00Z"));
        stay.addCharge(new BigDecimal("50000"));
        stay.markCheckoutPaymentPending();

        assertThat(stay.getOutstandingDepositAtCheckIn()).isEqualByComparingTo("100000.00");
        assertThat(stay.balance()).isEqualByComparingTo("850000.00");
        assertThatThrownBy(() -> stay.beginCheckout(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("settled or approved");

        UUID managerId = UUID.randomUUID();
        stay.beginCheckout(managerId, "Corporate guarantee");
        stay.close(Instant.parse("2026-07-18T04:00:00Z"));

        assertThat(stay.getStatus()).isEqualTo(StayStatus.CLOSED);
        assertThat(stay.getCheckoutOverrideApprovedBy()).isEqualTo(managerId);
        assertThat(stay.synchronizePaid(new BigDecimal("1050000"))).isTrue();
        assertThat(stay.balance()).isZero();
    }

    @Test
    void unacknowledgedOutstandingDepositBlocksCheckIn() {
        ReservationStayProjection reservation = reservation(
                new BigDecimal("1000000"), new BigDecimal("300000"), BigDecimal.ZERO);

        assertThatThrownBy(() -> Stay.pendingCheckIn(
                        reservation, UUID.randomUUID(), "b".repeat(64), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deposit");
    }

    @Test
    void aRecoveredSagaCanClearItsPreviousError() {
        ReservationStayProjection reservation = reservation(
                new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO);
        Stay stay = Stay.pendingCheckIn(
                reservation, UUID.randomUUID(), "c".repeat(64), true);
        stay.open(Instant.parse("2026-07-17T08:00:00Z"));
        stay.sagaFailed("Temporary inventory failure");

        stay.clearSagaError();

        assertThat(stay.getLastSagaError()).isNull();
    }

    private static ReservationStayProjection reservation(
            BigDecimal total, BigDecimal deposit, BigDecimal paid) {
        return new ReservationStayProjection(
                new ReservationSnapshot(
                        UUID.randomUUID(),
                        "RSV-STAY-01",
                        UUID.randomUUID(),
                        "Nguyen Van A",
                        "guest@example.com",
                        "0900000000",
                        "CONFIRMED",
                        1,
                        UUID.randomUUID(),
                        LocalDate.of(2026, 7, 17),
                        LocalDate.of(2026, 7, 19),
                        1,
                        1,
                        0,
                        "VND",
                        total,
                        deposit,
                        paid,
                        UUID.randomUUID()),
                Instant.now());
    }
}
