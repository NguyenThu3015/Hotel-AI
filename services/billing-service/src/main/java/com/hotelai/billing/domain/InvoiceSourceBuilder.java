package com.hotelai.billing.domain;

import com.hotelai.billing.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class InvoiceSourceBuilder {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private final ReservationBillingProjectionRepository reservations;
    private final StayFolioLineProjectionRepository folio;
    private final PaymentLedgerEntryRepository payments;
    private final ObjectMapper objectMapper;

    InvoiceSourceBuilder(
            ReservationBillingProjectionRepository reservations,
            StayFolioLineProjectionRepository folio,
            PaymentLedgerEntryRepository payments,
            ObjectMapper objectMapper) {
        this.reservations = reservations;
        this.folio = folio;
        this.payments = payments;
        this.objectMapper = objectMapper;
    }

    InvoiceSourceSnapshot build(StayBillingProjection stay) {
        ReservationBillingProjection reservation = reservations
                .findById(stay.getReservationId())
                .orElseThrow(() -> ApiException.conflict(
                        "reservation-projection-missing",
                        "Reservation financial snapshot is not available"));
        requireCurrency(stay.getCurrency(), reservation.getCurrency());
        requireEqual(
                reservation.calculatedTotal(),
                reservation.getTotalAmount(),
                "Reservation subtotal, discount and tax do not reconcile");

        List<StayFolioLineProjection> folioLines =
                folio.findByStayIdOrderByRecordedAtAsc(stay.getStayId());
        for (StayFolioLineProjection line : folioLines) {
            requireCurrency(stay.getCurrency(), line.getCurrency());
        }
        BigDecimal folioAmount = sum(folioLines.stream()
                .map(StayFolioLineProjection::getAmount)
                .toList());
        BigDecimal expectedCharges =
                reservation.getTotalAmount().add(folioAmount).setScale(2);
        requireEqual(
                expectedCharges,
                stay.getChargesTotal(),
                "Stay charges differ from reservation and folio lines");

        List<PaymentLedgerEntry> paymentEntries =
                payments.findByReservationIdOrderByOccurredAtAsc(stay.getReservationId());
        for (PaymentLedgerEntry entry : paymentEntries) {
            requireCurrency(stay.getCurrency(), entry.getCurrency());
        }
        BigDecimal paymentLineTotal =
                sum(paymentEntries.stream().map(PaymentLedgerEntry::signedAmount).toList());
        BigDecimal paidAmount = paymentLineTotal.negate().setScale(2);
        BigDecimal expectedBalance = expectedCharges.subtract(paidAmount).setScale(2);
        requireEqual(
                paidAmount,
                stay.getPaidTotal(),
                "Stay paid amount differs from the payment/refund ledger");
        requireEqual(
                expectedBalance,
                stay.getBalance(),
                "Stay balance differs from charges and payments");

        List<InvoiceLineSpec> lines = new ArrayList<>();
        int order = 10;
        BigDecimal roomQuantity = new BigDecimal(reservation.getRoomCount());
        lines.add(new InvoiceLineSpec(
                InvoiceLineCategory.ROOM,
                reservation.getReservationId(),
                "Room charges " + reservation.getCheckIn() + " to " + reservation.getCheckOut(),
                roomQuantity,
                reservation.getSubtotal().divide(
                        roomQuantity, 2, RoundingMode.HALF_UP),
                reservation.getSubtotal(),
                order));
        if (reservation.getDiscountAmount().signum() > 0) {
            order += 10;
            lines.add(new InvoiceLineSpec(
                    InvoiceLineCategory.DISCOUNT,
                    reservation.getReservationId(),
                    "Reservation discount",
                    BigDecimal.ONE,
                    reservation.getDiscountAmount().negate(),
                    reservation.getDiscountAmount().negate(),
                    order));
        }
        if (reservation.getTaxAmount().signum() > 0) {
            order += 10;
            lines.add(new InvoiceLineSpec(
                    InvoiceLineCategory.TAX,
                    reservation.getReservationId(),
                    "Reservation tax",
                    BigDecimal.ONE,
                    reservation.getTaxAmount(),
                    reservation.getTaxAmount(),
                    order));
        }
        for (StayFolioLineProjection line : folioLines) {
            order += 10;
            lines.add(new InvoiceLineSpec(
                    InvoiceLineCategory.FOLIO,
                    line.getEntryId(),
                    line.getDescription(),
                    BigDecimal.ONE,
                    line.getAmount(),
                    line.getAmount(),
                    order));
        }
        for (PaymentLedgerEntry entry : paymentEntries) {
            order += 10;
            BigDecimal signed = entry.signedAmount();
            lines.add(new InvoiceLineSpec(
                    "PAYMENT".equals(entry.getType())
                            ? InvoiceLineCategory.PAYMENT
                            : InvoiceLineCategory.REFUND,
                    entry.getEventId(),
                    entry.getType() + " " + entry.getProvider()
                            + (entry.getProviderReference() == null
                                ? ""
                                : " " + entry.getProviderReference()),
                    BigDecimal.ONE,
                    signed,
                    signed,
                    order));
        }

        String sourceHash = hash(stay, reservation, lines);
        return new InvoiceSourceSnapshot(
                stay.getStayId(),
                stay.getReservationId(),
                stay.getCustomerId(),
                stay.getReservationReference(),
                stay.getCurrency(),
                reservation.getSubtotal(),
                reservation.getDiscountAmount(),
                reservation.getTaxAmount(),
                folioAmount,
                expectedCharges,
                paidAmount,
                expectedBalance,
                sourceHash,
                List.copyOf(lines));
    }

    private String hash(
            StayBillingProjection stay,
            ReservationBillingProjection reservation,
            List<InvoiceLineSpec> lines) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("stayId", stay.getStayId());
            value.put("stayUpdatedAt", stay.getSourceUpdatedAt());
            value.put("reservationVersion", reservation.getSourceVersion());
            value.put("currency", stay.getCurrency());
            value.put("charges", stay.getChargesTotal());
            value.put("paid", stay.getPaidTotal());
            value.put("balance", stay.getBalance());
            value.put("lines", lines);
            byte[] canonical = objectMapper.writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash invoice source", error);
        }
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2);
    }

    private static void requireCurrency(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw ApiException.conflict(
                    "invoice-currency-mismatch",
                    "Invoice source currencies do not match");
        }
    }

    private static void requireEqual(
            BigDecimal expected, BigDecimal actual, String message) {
        if (expected.subtract(actual).abs().compareTo(TOLERANCE) > 0) {
            throw ApiException.conflict("invoice-not-balanced", message);
        }
    }
}
