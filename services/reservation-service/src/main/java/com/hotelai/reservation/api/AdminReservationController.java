package com.hotelai.reservation.api;

import com.hotelai.reservation.domain.Reservation;
import com.hotelai.reservation.domain.ReservationApplicationService;
import com.hotelai.reservation.domain.ReservationChannel;
import com.hotelai.reservation.domain.ReservationChangeRequest;
import com.hotelai.reservation.domain.ReservationStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping({"/api/v1/admin/reservations", "/api/v1/admin/bookings"})
public class AdminReservationController {
    private final ReservationApplicationService service;
    private final ObjectMapper objectMapper;

    public AdminReservationController(ReservationApplicationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ReservationDtos.ReservationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "STAFF") ReservationChannel channel,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.CreateRequest request) {
        Reservation created = service.createByStaff(
                CustomerReservationController.actor(jwt), customerId, channel, request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/admin/reservations/" + created.getId()))
                .body(ReservationDtos.ReservationResponse.from(created, objectMapper, true));
    }

    @GetMapping
    public Page<ReservationDtos.ReservationResponse> history(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInTo,
            @RequestParam(required = false) String reference,
            @PageableDefault(size = 30, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
                    Pageable pageable) {
        return service.staffHistory(status, checkInFrom, checkInTo, reference, pageable)
                .map(value -> ReservationDtos.ReservationResponse.from(value, objectMapper, false));
    }

    @GetMapping("/{reservationId}")
    public ReservationDtos.ReservationResponse detail(@PathVariable UUID reservationId) {
        return ReservationDtos.ReservationResponse.from(service.getForStaff(reservationId), objectMapper, true);
    }

    @PutMapping("/{reservationId}")
    public ReservationDtos.ChangeOutcomeResponse change(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.ChangeRequest request) {
        return ReservationDtos.ChangeOutcomeResponse.from(
                service.changeByStaff(
                        CustomerReservationController.actor(jwt), reservationId, request, idempotencyKey),
                objectMapper);
    }

    @PostMapping("/{reservationId}/confirm")
    public ReservationDtos.ReservationResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.VersionRequest request) {
        return ReservationDtos.ReservationResponse.from(
                service.confirm(
                        CustomerReservationController.actor(jwt), reservationId,
                        request.expectedVersion(), idempotencyKey),
                objectMapper,
                false);
    }

    @PostMapping("/{reservationId}/reject")
    public ReservationDtos.ReservationResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.VersionedReasonRequest request) {
        return ReservationDtos.ReservationResponse.from(
                service.reject(
                        CustomerReservationController.actor(jwt), reservationId,
                        request.expectedVersion(), request.reason(), idempotencyKey),
                objectMapper,
                false);
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationDtos.ReservationResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.VersionedReasonRequest request) {
        return ReservationDtos.ReservationResponse.from(
                service.cancelByStaff(
                        CustomerReservationController.actor(jwt), reservationId,
                        request.expectedVersion(), request.reason(), idempotencyKey),
                objectMapper,
                false);
    }

    @GetMapping("/{reservationId}/change-requests")
    public List<ReservationDtos.ChangeRequestResponse> changeRequests(@PathVariable UUID reservationId) {
        return service.changesForStaff(reservationId).stream()
                .map(value -> ReservationDtos.ChangeRequestResponse.from(value, objectMapper))
                .toList();
    }

    @PostMapping("/change-requests/{changeRequestId}/approve")
    public ReservationDtos.ReservationResponse approveChange(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID changeRequestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.VersionRequest request) {
        return ReservationDtos.ReservationResponse.from(
                service.approveChange(
                        CustomerReservationController.actor(jwt), changeRequestId,
                        request.expectedVersion(), idempotencyKey),
                objectMapper,
                false);
    }

    @PostMapping("/change-requests/{changeRequestId}/reject")
    public ReservationDtos.ChangeRequestResponse rejectChange(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID changeRequestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationDtos.VersionedReasonRequest request) {
        ReservationChangeRequest rejected = service.rejectChange(
                CustomerReservationController.actor(jwt), changeRequestId,
                request.expectedVersion(), request.reason(), idempotencyKey);
        return ReservationDtos.ChangeRequestResponse.from(rejected, objectMapper);
    }
}
