package com.hotelai.stay.api;

import com.hotelai.stay.domain.AssignmentStatus;
import com.hotelai.stay.domain.OperationStatus;
import com.hotelai.stay.domain.OperationType;
import com.hotelai.stay.domain.StayStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StayDtos {
    private StayDtos() {}

    public record GuestRequest(
            @NotBlank @Size(max = 180) String fullName,
            @NotNull LocalDate dateOfBirth,
            @NotBlank @Size(max = 80) String nationality,
            @NotBlank @Size(max = 30) String documentType,
            @NotBlank @Size(max = 120) String documentNumber,
            boolean primaryGuest) {}

    public record CheckInRequest(
            @NotNull UUID reservationId,
            @NotEmpty @Size(max = 20) List<@NotNull UUID> roomIds,
            @NotEmpty @Size(max = 60) List<@Valid GuestRequest> guests,
            boolean outstandingDepositAcknowledged,
            boolean earlyCheckInApproved) {}

    public record RoomChangeRequest(
            @NotNull UUID fromRoomId,
            @NotNull UUID targetRoomId,
            @NotNull BigDecimal priceDelta,
            @NotBlank @Size(max = 500) String reason) {}

    public record ExtensionRequest(
            @NotNull LocalDate newCheckoutDate,
            @NotNull BigDecimal priceDelta,
            @NotBlank @Size(max = 500) String reason) {}

    public record IncidentalRequest(
            @NotBlank @Size(max = 40) String type,
            @NotBlank @Size(max = 300) String description,
            @NotNull BigDecimal amount) {}

    public record CheckoutRequest(
            @Size(max = 100) List<@Valid IncidentalRequest> incidentals,
            boolean debtOverride,
            @Size(max = 500) String debtOverrideReason) {}

    public record ContinueCheckoutRequest(@Size(max = 500) String overrideReason) {}

    public record GuestView(
            UUID id,
            String fullName,
            LocalDate dateOfBirth,
            String nationality,
            String documentType,
            String documentNumberMasked,
            boolean primaryGuest) {}

    public record AssignmentView(
            UUID id,
            UUID roomId,
            String roomNumber,
            UUID roomTypeId,
            AssignmentStatus status,
            Instant assignedAt,
            Instant endedAt,
            String reason) {}

    public record FolioEntryView(
            UUID id,
            String type,
            String description,
            BigDecimal amount,
            String currency,
            Instant recordedAt) {}

    public record OperationView(
            UUID id,
            OperationType type,
            OperationStatus status,
            UUID fromRoomId,
            UUID targetRoomId,
            LocalDate requestedCheckoutDate,
            BigDecimal amountDelta,
            String reason,
            String lastError,
            Instant createdAt) {}

    public record StayView(
            UUID id,
            UUID reservationId,
            UUID customerId,
            String reservationReference,
            String contactName,
            String contactEmail,
            String contactPhone,
            StayStatus status,
            LocalDate scheduledCheckIn,
            LocalDate scheduledCheckOut,
            Instant actualCheckIn,
            Instant actualCheckOut,
            int adults,
            int children,
            String currency,
            BigDecimal chargesTotal,
            BigDecimal paidTotal,
            BigDecimal balance,
            BigDecimal outstandingDepositAtCheckIn,
            UUID checkoutOverrideApprovedBy,
            String checkoutOverrideReason,
            String lastSagaError,
            long version,
            List<GuestView> guests,
            List<AssignmentView> assignments,
            List<FolioEntryView> folio,
            List<OperationView> operations) {}
}
