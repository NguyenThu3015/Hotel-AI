package com.hotelai.stay.api;

import static com.hotelai.stay.api.StayDtos.*;

import com.hotelai.stay.domain.RoomAssignment;
import com.hotelai.stay.domain.Stay;
import com.hotelai.stay.domain.StayApplicationService.StayDetails;
import com.hotelai.stay.domain.StayFolioEntry;
import com.hotelai.stay.domain.StayGuest;
import com.hotelai.stay.domain.StayOperation;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

final class StayApiSupport {
    private StayApiSupport() {}

    static UUID actor(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Authenticated uid must be a UUID");
        }
    }

    static boolean manager(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(value -> value.equals("ROLE_MANAGER") || value.equals("ROLE_ADMIN"));
    }

    static StayView view(StayDetails details) {
        Stay stay = details.stay();
        return new StayView(
                stay.getId(),
                stay.getReservationId(),
                stay.getCustomerId(),
                stay.getReservationReference(),
                stay.getContactName(),
                stay.getContactEmail(),
                stay.getContactPhone(),
                stay.getStatus(),
                stay.getScheduledCheckIn(),
                stay.getScheduledCheckOut(),
                stay.getActualCheckIn(),
                stay.getActualCheckOut(),
                stay.getAdults(),
                stay.getChildren(),
                stay.getCurrency(),
                stay.getChargesTotal(),
                stay.getPaidTotal(),
                stay.balance(),
                stay.getOutstandingDepositAtCheckIn(),
                stay.getCheckoutOverrideApprovedBy(),
                stay.getCheckoutOverrideReason(),
                stay.getLastSagaError(),
                stay.getVersion(),
                details.guests().stream().map(StayApiSupport::guest).toList(),
                details.assignments().stream().map(StayApiSupport::assignment).toList(),
                details.folio().stream().map(StayApiSupport::folio).toList(),
                details.operations().stream().map(StayApiSupport::operation).toList());
    }

    private static GuestView guest(StayGuest value) {
        return new GuestView(
                value.getId(),
                value.getFullName(),
                value.getDateOfBirth(),
                value.getNationality(),
                value.getDocumentType(),
                "****" + value.getDocumentNumberLast4(),
                value.isPrimaryGuest());
    }

    private static AssignmentView assignment(RoomAssignment value) {
        return new AssignmentView(
                value.getId(),
                value.getRoomId(),
                value.getRoomNumber(),
                value.getRoomTypeId(),
                value.getStatus(),
                value.getAssignedAt(),
                value.getEndedAt(),
                value.getReason());
    }

    private static FolioEntryView folio(StayFolioEntry value) {
        return new FolioEntryView(
                value.getId(),
                value.getEntryType(),
                value.getDescription(),
                value.getAmount(),
                value.getCurrency(),
                value.getRecordedAt());
    }

    private static OperationView operation(StayOperation value) {
        return new OperationView(
                value.getId(),
                value.getType(),
                value.getStatus(),
                value.getFromRoomId(),
                value.getTargetRoomId(),
                value.getRequestedCheckoutDate(),
                value.getAmountDelta(),
                value.getReason(),
                value.getLastError(),
                value.getCreatedAt());
    }
}
