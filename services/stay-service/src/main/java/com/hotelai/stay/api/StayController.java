package com.hotelai.stay.api;

import static com.hotelai.stay.api.StayApiSupport.*;
import static com.hotelai.stay.api.StayDtos.*;

import com.hotelai.stay.domain.StayApplicationService;
import com.hotelai.stay.domain.StayApplicationService.ReservationSummary;
import com.hotelai.stay.domain.StayCommands;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class StayController {
    private final StayApplicationService stays;

    public StayController(StayApplicationService stays) {
        this.stays = stays;
    }

    @GetMapping("/stay-reservations")
    List<ReservationSummary> reservations(
            @RequestParam(defaultValue = "") String query) {
        return stays.searchReservations(query);
    }

    @PostMapping("/stays/check-ins")
    ResponseEntity<StayView> checkIn(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CheckInRequest request) {
        StayView result = view(stays.checkIn(
                actor(authentication),
                new StayCommands.CheckIn(
                        request.reservationId(),
                        request.roomIds(),
                        request.guests().stream().map(value -> new StayCommands.Guest(
                                value.fullName(),
                                value.dateOfBirth(),
                                value.nationality(),
                                value.documentType(),
                                value.documentNumber(),
                                value.primaryGuest())).toList(),
                        request.outstandingDepositAcknowledged(),
                        request.earlyCheckInApproved()),
                key));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/admin/stays/" + result.id()))
                .body(result);
    }

    @GetMapping("/stays/{stayId}")
    StayView stay(@PathVariable UUID stayId) {
        return view(stays.details(stayId));
    }

    @PostMapping("/stays/{stayId}/room-changes")
    ResponseEntity<StayView> changeRoom(
            Authentication authentication,
            @PathVariable UUID stayId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RoomChangeRequest request) {
        return ResponseEntity.accepted().body(view(stays.changeRoom(
                actor(authentication),
                stayId,
                new StayCommands.ChangeRoom(
                        request.fromRoomId(), request.targetRoomId(),
                        request.priceDelta(), request.reason()),
                key)));
    }

    @PostMapping("/stays/{stayId}/extensions")
    ResponseEntity<StayView> extend(
            Authentication authentication,
            @PathVariable UUID stayId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ExtensionRequest request) {
        return ResponseEntity.accepted().body(view(stays.extend(
                actor(authentication),
                stayId,
                new StayCommands.Extend(
                        request.newCheckoutDate(), request.priceDelta(), request.reason()),
                key)));
    }

    @PostMapping("/stays/{stayId}/check-out")
    ResponseEntity<StayView> checkout(
            Authentication authentication,
            @PathVariable UUID stayId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CheckoutRequest request) {
        List<StayCommands.Incidental> incidentals = request.incidentals() == null
                ? List.of()
                : request.incidentals().stream()
                        .map(value -> new StayCommands.Incidental(
                                value.type(), value.description(), value.amount()))
                        .toList();
        return ResponseEntity.accepted().body(view(stays.checkout(
                actor(authentication),
                manager(authentication),
                stayId,
                new StayCommands.Checkout(
                        incidentals, request.debtOverride(), request.debtOverrideReason()),
                key)));
    }

    @PostMapping("/stays/{stayId}/check-out/continue")
    ResponseEntity<StayView> continueCheckout(
            Authentication authentication,
            @PathVariable UUID stayId,
            @Valid @RequestBody ContinueCheckoutRequest request) {
        return ResponseEntity.accepted().body(view(stays.continueCheckout(
                actor(authentication),
                manager(authentication),
                stayId,
                request.overrideReason())));
    }
}
