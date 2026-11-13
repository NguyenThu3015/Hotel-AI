package com.hotelai.reservation.api;

import com.hotelai.reservation.domain.Reservation;
import com.hotelai.reservation.domain.ReservationAmendment;
import com.hotelai.reservation.domain.ReservationApplicationService;
import com.hotelai.reservation.domain.ReservationChangeRequest;
import com.hotelai.reservation.domain.ReservationChannel;
import com.hotelai.reservation.domain.ReservationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ReservationDtos {
    private ReservationDtos() {}

    public record GuestRequest(
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 30) String documentType,
            @Size(max = 80) String documentNumber,
            @Pattern(regexp = "^[A-Za-z]{2}$") String nationality,
            boolean primary) {}

    public record CreateRequest(
            @NotNull UUID roomTypeId,
            @NotNull @FutureOrPresent LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) @Max(20) int roomCount,
            @Min(1) @Max(100) int adults,
            @Min(0) @Max(100) int children,
            @NotBlank @Size(max = 160) String contactName,
            @NotBlank @Email @Size(max = 320) String contactEmail,
            @NotBlank @Pattern(regexp = "^[+0-9][0-9 .()-]{7,29}$") String contactPhone,
            @Size(max = 1000) String specialRequests,
            @Size(max = 60) String promotionCode,
            @Valid @Size(max = 100) List<GuestRequest> guests) {}

    public record ChangeRequest(
            @NotNull UUID roomTypeId,
            @NotNull @FutureOrPresent LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) @Max(20) int roomCount,
            @Min(1) @Max(100) int adults,
            @Min(0) @Max(100) int children,
            @Size(max = 1000) String specialRequests,
            @Size(max = 60) String promotionCode,
            @Min(0) long expectedVersion) {
        public ReservationAmendment toAmendment() {
            return new ReservationAmendment(
                    roomTypeId, checkIn, checkOut, roomCount, adults, children,
                    specialRequests, promotionCode, expectedVersion);
        }
    }

    public record VersionedReasonRequest(
            @Min(0) long expectedVersion,
            @NotBlank @Size(max = 500) String reason) {}

    public record VersionRequest(@Min(0) long expectedVersion) {}

    public record StaffCreateParameters(@NotNull UUID customerId, @NotNull ReservationChannel channel) {}

    public record GuestResponse(
            UUID id,
            String fullName,
            String documentType,
            String documentNumber,
            String nationality,
            boolean primary) {}

    public record ReservationResponse(
            UUID id,
            String reference,
            UUID customerId,
            UUID createdByIdentityId,
            ReservationChannel channel,
            ReservationStatus status,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String contactName,
            String contactEmail,
            String contactPhone,
            String specialRequests,
            UUID pricingQuoteId,
            String pricingVersion,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount,
            boolean manualConfirmationRequired,
            JsonNode priceBreakdown,
            JsonNode cancellationPolicy,
            Instant freeCancellationUntil,
            BigDecimal cancellationFeeApplied,
            Instant holdExpiresAt,
            Instant confirmedAt,
            Instant cancelledAt,
            String cancellationReason,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<GuestResponse> guests) {

        public static ReservationResponse from(Reservation value, ObjectMapper mapper, boolean includeGuests) {
            List<GuestResponse> guestResponses = includeGuests
                    ? value.getGuests().stream().map(guest -> new GuestResponse(
                            guest.getId(), guest.getFullName(), guest.getDocumentType(), guest.getDocumentNumber(),
                            guest.getNationality(), guest.isPrimary())).toList()
                    : List.of();
            return new ReservationResponse(
                    value.getId(), value.getReference(), value.getCustomerId(), value.getCreatedByIdentityId(),
                    value.getChannel(), value.getStatus(), value.getRoomTypeId(), value.getCheckIn(),
                    value.getCheckOut(), value.getRoomCount(), value.getAdults(), value.getChildren(),
                    value.getContactName(), value.getContactEmail(), value.getContactPhone(),
                    value.getSpecialRequests(), value.getPricingQuoteId(), value.getPricingVersion(),
                    value.getCurrency(), value.getSubtotal(), value.getDiscountAmount(), value.getTaxAmount(),
                    value.getTotalAmount(), value.getDepositRequired(), value.getPaidAmount(),
                    value.isManualConfirmationRequired(), tree(mapper, value.getPriceBreakdown()),
                    tree(mapper, value.getCancellationPolicy()), value.getFreeCancellationUntil(),
                    value.getCancellationFeeApplied(), value.getHoldExpiresAt(), value.getConfirmedAt(),
                    value.getCancelledAt(), value.getCancellationReason(), value.getCreatedAt(),
                    value.getUpdatedAt(), value.getVersion(), guestResponses);
        }
    }

    public record ChangeRequestResponse(
            UUID id,
            UUID reservationId,
            UUID requestedBy,
            String status,
            JsonNode requestedChange,
            String decisionReason,
            UUID decidedBy,
            Instant decidedAt,
            Instant createdAt,
            long version) {
        public static ChangeRequestResponse from(ReservationChangeRequest value, ObjectMapper mapper) {
            return new ChangeRequestResponse(
                    value.getId(), value.getReservationId(), value.getRequestedBy(), value.getStatus().name(),
                    tree(mapper, value.getRequestedChange()), value.getDecisionReason(), value.getDecidedBy(),
                    value.getDecidedAt(), value.getCreatedAt(), value.getVersion());
        }
    }

    public record ChangeOutcomeResponse(
            boolean applied,
            ReservationResponse reservation,
            ChangeRequestResponse changeRequest) {
        public static ChangeOutcomeResponse from(
                ReservationApplicationService.ChangeOutcome value, ObjectMapper mapper) {
            return new ChangeOutcomeResponse(
                    value.applied(),
                    ReservationResponse.from(value.reservation(), mapper, false),
                    value.changeRequest() == null ? null : ChangeRequestResponse.from(value.changeRequest(), mapper));
        }
    }

    private static JsonNode tree(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException("Stored reservation JSON is invalid", error);
        }
    }
}
