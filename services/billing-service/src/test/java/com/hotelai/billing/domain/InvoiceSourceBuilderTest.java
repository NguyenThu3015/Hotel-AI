package com.hotelai.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hotelai.billing.common.ApiException;
import com.hotelai.billing.domain.ReservationBillingProjection.ReservationSnapshot;
import com.hotelai.billing.domain.StayBillingProjection.FolioLineSnapshot;
import com.hotelai.billing.domain.StayBillingProjection.StaySnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InvoiceSourceBuilderTest {

    @Test
    void reconcilesRoomFolioPaymentAndRefundLines() {
        Fixture value = fixture("1150000.00", "1000000.00", "150000.00");

        InvoiceSourceSnapshot result = value.builder().build(value.stay());

        assertThat(result.totalAmount()).isEqualByComparingTo("1150000.00");
        assertThat(result.paidAmount()).isEqualByComparingTo("1000000.00");
        assertThat(result.balance()).isEqualByComparingTo("150000.00");
        assertThat(result.lines())
                .extracting(InvoiceLineSpec::category)
                .containsExactly(
                        InvoiceLineCategory.ROOM,
                        InvoiceLineCategory.FOLIO,
                        InvoiceLineCategory.PAYMENT,
                        InvoiceLineCategory.REFUND);
        assertThat(result.sourceHash()).hasSize(64);
    }

    @Test
    void rejectsStayWhoseAmountsDifferFromSourceLedgers() {
        Fixture value = fixture("1200000.00", "1000000.00", "200000.00");

        assertThatThrownBy(() -> value.builder().build(value.stay()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("invoice-not-balanced");
    }

    private static Fixture fixture(
            String stayCharges, String stayPaid, String stayBalance) {
        UUID reservationId = UUID.randomUUID();
        UUID stayId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant eventTime = Instant.parse("2026-07-17T08:00:00Z");

        ReservationBillingProjection reservation = new ReservationBillingProjection();
        reservation.synchronize(
                new ReservationSnapshot(
                        reservationId,
                        "BK-2026-0001",
                        customerId,
                        "Nguyen Van A",
                        "guest@example.com",
                        "0900000000",
                        "CHECKED_IN",
                        UUID.randomUUID(),
                        LocalDate.of(2026, 7, 16),
                        LocalDate.of(2026, 7, 17),
                        1,
                        2,
                        0,
                        "VND",
                        new BigDecimal("1000000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("1000000"),
                        new BigDecimal("500000"),
                        new BigDecimal("1000000"),
                        5),
                eventTime,
                "{}");

        StayBillingProjection stay = new StayBillingProjection();
        stay.synchronize(
                new StaySnapshot(
                        stayId,
                        reservationId,
                        customerId,
                        "BK-2026-0001",
                        "Nguyen Van A",
                        "guest@example.com",
                        "0900000000",
                        "CLOSED",
                        LocalDate.of(2026, 7, 16),
                        LocalDate.of(2026, 7, 17),
                        eventTime.minusSeconds(86400),
                        eventTime,
                        List.of(UUID.randomUUID()),
                        List.of("101"),
                        "VND",
                        new BigDecimal(stayCharges),
                        new BigDecimal(stayPaid),
                        new BigDecimal(stayBalance),
                        List.of()),
                eventTime);

        FolioLineSnapshot folioSnapshot = new FolioLineSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HOTEL_SERVICE",
                "Dịch vụ spa",
                new BigDecimal("150000"),
                "VND",
                UUID.randomUUID(),
                eventTime.minusSeconds(1800));
        StayFolioLineProjection folioLine =
                new StayFolioLineProjection(stayId, folioSnapshot);

        PaymentLedgerEntry payment = new PaymentLedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                reservationId,
                "PAYMENT",
                new BigDecimal("1100000"),
                "VND",
                "MOMO",
                "momo-001",
                eventTime.minusSeconds(1200));
        PaymentLedgerEntry refund = new PaymentLedgerEntry(
                UUID.randomUUID(),
                payment.getPaymentId(),
                reservationId,
                "REFUND",
                new BigDecimal("100000"),
                "VND",
                "MOMO",
                "momo-refund-001",
                eventTime.minusSeconds(600));

        ReservationBillingProjectionRepository reservations =
                mock(ReservationBillingProjectionRepository.class);
        StayFolioLineProjectionRepository folio =
                mock(StayFolioLineProjectionRepository.class);
        PaymentLedgerEntryRepository payments =
                mock(PaymentLedgerEntryRepository.class);
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(folio.findByStayIdOrderByRecordedAtAsc(stayId))
                .thenReturn(List.of(folioLine));
        when(payments.findByReservationIdOrderByOccurredAtAsc(reservationId))
                .thenReturn(List.of(payment, refund));

        return new Fixture(
                stay,
                new InvoiceSourceBuilder(
                        reservations, folio, payments, new ObjectMapper()));
    }

    private record Fixture(
            StayBillingProjection stay, InvoiceSourceBuilder builder) {}
}
