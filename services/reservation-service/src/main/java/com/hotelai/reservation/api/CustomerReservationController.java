package com.hotelai.reservation.api;

import com.hotelai.reservation.domain.Reservation;
import com.hotelai.reservation.domain.ReservationApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping({"/api/v1/reservations", "/api/v1/bookings"})
public class CustomerReservationController {
    private final ReservationApplicationService service;
    private final ObjectMapper objectMapper;

    public CustomerReservationController(ReservationApplicationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ReservationDtos.ReservationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.CreateRequest request) {
        Reservation created = service.createForCustomer(actor(jwt), request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + created.getId()))
                .body(ReservationDtos.ReservationResponse.from(created, objectMapper, true));
    }

    @GetMapping
    public Page<ReservationDtos.ReservationResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable) {
        return service.customerHistory(actor(jwt), pageable)
                .map(value -> ReservationDtos.ReservationResponse.from(value, objectMapper, false));
    }

    @GetMapping("/{reservationId}")
    public ReservationDtos.ReservationResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId) {
        return ReservationDtos.ReservationResponse.from(
                service.getForCustomer(actor(jwt), reservationId), objectMapper, true);
    }

    @GetMapping("/{reservationId}/change-requests")
    public List<ReservationDtos.ChangeRequestResponse> changeRequests(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId) {
        return service.changesForCustomer(actor(jwt), reservationId).stream()
                .map(value -> ReservationDtos.ChangeRequestResponse.from(value, objectMapper))
                .toList();
    }

    @PutMapping("/{reservationId}")
    public ResponseEntity<ReservationDtos.ChangeOutcomeResponse> change(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.ChangeRequest request) {
        ReservationApplicationService.ChangeOutcome outcome =
                service.changeForCustomer(actor(jwt), reservationId, request, idempotencyKey);
        ReservationDtos.ChangeOutcomeResponse response = ReservationDtos.ChangeOutcomeResponse.from(outcome, objectMapper);
        return outcome.applied() ? ResponseEntity.ok(response) : ResponseEntity.accepted().body(response);
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationDtos.ReservationResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.VersionedReasonRequest request) {
        return ReservationDtos.ReservationResponse.from(
                service.cancelForCustomer(
                        actor(jwt), reservationId, request.expectedVersion(), request.reason(), idempotencyKey),
                objectMapper,
                false);
    }

    static UUID actor(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        return UUID.fromString(uid);
    }
}
