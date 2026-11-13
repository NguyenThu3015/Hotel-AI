package com.hotelai.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationStateMachineTest {
    @Test
    void requiredDepositMovesFromPendingPaymentToConfirmed() {
        Reservation reservation = reservation(price("100.00", "30.00", false, Instant.now().plusSeconds(3600), "25.00"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        reservation.recordPayment(new BigDecimal("10.00"), "VND");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        reservation.recordPayment(new BigDecimal("20.00"), "VND");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getPaidAmount()).isEqualByComparingTo("30.00");
        assertThat(reservation.getHoldExpiresAt()).isNull();
    }

    @Test
    void paidManualReservationStillRequiresStaffDecision() {
        Reservation reservation = reservation(price("100.00", "30.00", true, null, "0"));

        reservation.recordPayment(new BigDecimal("30.00"), "VND");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_CONFIRMATION);
        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending manual");
    }

    @Test
    void cancellationUsesImmutablePolicySnapshot() {
        Reservation reservation = reservation(price(
                "100.00", "0", false, Instant.now().minusSeconds(1), "25.00"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        BigDecimal fee = reservation.cancel("Plans changed", Instant.now());

        assertThat(fee).isEqualByComparingTo("25.00");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThatThrownBy(() -> reservation.cancel("Again", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiredHoldCannotLaterAcceptPayment() {
        Reservation reservation = reservation(price("100.00", "30.00", false, null, "0"));
        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThatThrownBy(() -> reservation.recordPayment(new BigDecimal("30"), "VND"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completedRefundReducesPaidSnapshotWithoutReopeningReservation() {
        Reservation reservation =
                reservation(price("100.00", "30.00", false, null, "0"));
        reservation.recordPayment(new BigDecimal("30.00"), "VND");

        reservation.recordRefund(new BigDecimal("10.00"), "VND");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThatThrownBy(() -> reservation.recordRefund(new BigDecimal("30.00"), "VND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void confirmedReservationCanRecordAFullPayment() {
        Reservation reservation = reservation(price("100.00", "0", false, null, "0"));

        reservation.recordPayment(new BigDecimal("100.00"), "VND");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getPaidAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void stayEventsFollowConfirmedCheckedInCheckedOutSequence() {
        Reservation reservation = reservation(price("100.00", "0", false, null, "0"));

        reservation.checkIn();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        reservation.checkOut();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_OUT);
        assertThatThrownBy(reservation::checkIn).isInstanceOf(IllegalStateException.class);
    }

    private static Reservation reservation(PricingSnapshot price) {
        UUID id = UUID.randomUUID();
        return Reservation.create(
                id,
                "RSV20260717ABCDEF12",
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReservationChannel.ONLINE,
                UUID.randomUUID(),
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                1,
                2,
                0,
                "Nguyen An",
                "an@example.com",
                "0900000000",
                null,
                new InventoryHold(UUID.randomUUID(), Instant.now().plusSeconds(900), 0),
                price);
    }

    private static PricingSnapshot price(
            String total, String deposit, boolean manual, Instant freeUntil, String cancellationFee) {
        return new PricingSnapshot(
                UUID.randomUUID(),
                "price-v1",
                "VND",
                new BigDecimal(total),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(total),
                new BigDecimal(deposit),
                manual,
                Instant.now().plusSeconds(300),
                "{\"nights\":2}",
                "{\"code\":\"FLEX\"}",
                freeUntil,
                new BigDecimal(cancellationFee));
    }
}
