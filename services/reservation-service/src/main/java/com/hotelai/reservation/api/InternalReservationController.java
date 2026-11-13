package com.hotelai.reservation.api;

import com.hotelai.reservation.domain.Reservation;
import com.hotelai.reservation.domain.ReservationApplicationService;
import com.hotelai.reservation.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/reservations")
public class InternalReservationController {
    private final ReservationApplicationService reservations;

    public InternalReservationController(ReservationApplicationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping("/{reservationId}/payment-context")
    public PaymentContext paymentContext(@PathVariable UUID reservationId) {
        Reservation value = reservations.getInternal(reservationId);
        BigDecimal depositDue = value.getDepositRequired().subtract(value.getPaidAmount()).max(BigDecimal.ZERO);
        return new PaymentContext(
                value.getId(), value.getCustomerId(), value.getStatus(), value.getCurrency(),
                value.getTotalAmount(), value.getDepositRequired(), value.getPaidAmount(), depositDue,
                value.getCancellationFeeApplied(), value.getVersion());
    }

    @GetMapping("/{reservationId}/stay-context")
    public StayContext stayContext(@PathVariable UUID reservationId) {
        Reservation value = reservations.getInternal(reservationId);
        return new StayContext(
                value.getId(), value.getCustomerId(), value.getStatus(), value.getRoomTypeId(),
                value.getCheckIn(), value.getCheckOut(), value.getRoomCount(), value.getAdults(),
                value.getChildren(), value.getVersion());
    }

    public record PaymentContext(
            UUID reservationId,
            UUID customerId,
            ReservationStatus status,
            String currency,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            BigDecimal depositDue,
            BigDecimal cancellationFeeApplied,
            long version) {}

    public record StayContext(
            UUID reservationId,
            UUID customerId,
            ReservationStatus status,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            long version) {}
}
