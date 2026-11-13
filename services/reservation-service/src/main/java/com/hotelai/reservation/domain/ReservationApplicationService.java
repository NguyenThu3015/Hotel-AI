package com.hotelai.reservation.domain;

import com.hotelai.reservation.api.ReservationDtos;
import com.hotelai.reservation.common.ApiException;
import com.hotelai.reservation.config.CorrelationIdFilter;
import com.hotelai.reservation.config.ReservationProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReservationApplicationService {
    private final ReservationRepository reservations;
    private final ReservationChangeRequestRepository changes;
    private final IdempotencyRecordRepository idempotency;
    private final InventoryGateway inventory;
    private final PricingGateway pricing;
    private final ReservationEventService events;
    private final ObjectMapper objectMapper;
    private final ReservationProperties properties;

    public ReservationApplicationService(
            ReservationRepository reservations,
            ReservationChangeRequestRepository changes,
            IdempotencyRecordRepository idempotency,
            InventoryGateway inventory,
            PricingGateway pricing,
            ReservationEventService events,
            ObjectMapper objectMapper,
            ReservationProperties properties) {
        this.reservations = reservations;
        this.changes = changes;
        this.idempotency = idempotency;
        this.inventory = inventory;
        this.pricing = pricing;
        this.events = events;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public Reservation createForCustomer(UUID actorId, ReservationDtos.CreateRequest request, String key) {
        return create(actorId, actorId, ReservationChannel.ONLINE, request, key);
    }

    @Transactional
    public Reservation createByStaff(
            UUID actorId,
            UUID customerId,
            ReservationChannel channel,
            ReservationDtos.CreateRequest request,
            String key) {
        if (channel == ReservationChannel.ONLINE) {
            throw ApiException.validation("Staff-created reservations must use PHONE, WALK_IN, or STAFF channel");
        }
        return create(actorId, customerId, channel, request, key);
    }

    private Reservation create(
            UUID actorId,
            UUID customerId,
            ReservationChannel channel,
            ReservationDtos.CreateRequest request,
            String key) {
        requireKey(key);
        validateStay(request.checkIn(), request.checkOut(), request.roomCount(), request.adults(), request.children());
        validateGuests(request.guests());
        String hash = hash(request, customerId, channel);
        Optional<IdempotencyRecord> replay = replay(key, "CREATE", actorId);
        if (replay.isPresent()) return replayReservation(replay.get(), hash);

        UUID reservationId = UUID.randomUUID();
        PricingSnapshot quote = pricing.quote(new PricingGateway.QuoteCommand(
                customerId, request.roomTypeId(), request.checkIn(), request.checkOut(), request.roomCount(),
                request.adults(), request.children(), trimToNull(request.promotionCode()), channel.name()));
        validateQuote(quote);
        InventoryHold hold = inventory.acquire(
                new InventoryGateway.HoldCommand(
                        reservationId, request.roomTypeId(), request.checkIn(), request.checkOut(), request.roomCount(),
                        Instant.now().plus(properties.reservation().holdDuration())),
                "reservation-create:" + reservationId);

        try {
            pricing.synchronizePromotion(
                    reservationId,
                    quote.quoteId(),
                    customerId,
                    false,
                    promotionCommandKey("create"));
            Reservation reservation = Reservation.create(
                    reservationId, reference(reservationId), customerId, actorId, channel, request.roomTypeId(),
                    request.checkIn(), request.checkOut(), request.roomCount(), request.adults(), request.children(),
                    request.contactName(), request.contactEmail(), request.contactPhone(), request.specialRequests(),
                    hold, quote);
            addGuests(reservation, request);
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                inventory.confirm(hold.holdId(), reservationId);
            }
            reservations.save(reservation);
            events.append(reservation, "reservation.created.v1", false, Map.of("channel", channel.name()));
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                events.append(reservation, "reservation.confirmed.v1", false, Map.of("confirmationMode", "AUTOMATIC"));
            }
            idempotency.save(new IdempotencyRecord(
                    key, "CREATE", actorId, hash, "RESERVATION", reservation.getId()));
            return reservation;
        } catch (RuntimeException error) {
            bestEffortRelease(hold.holdId(), "reservation-create-failed");
            bestEffortReleasePromotion(reservationId);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public Reservation getForCustomer(UUID actorId, UUID reservationId) {
        Reservation reservation = detailed(reservationId);
        requireOwner(reservation, actorId);
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<ReservationChangeRequest> changesForCustomer(UUID actorId, UUID reservationId) {
        Reservation reservation = required(reservationId);
        requireOwner(reservation, actorId);
        return changes.findByReservationIdOrderByCreatedAtDesc(reservationId);
    }

    @Transactional(readOnly = true)
    public Reservation getForStaff(UUID reservationId) {
        return detailed(reservationId);
    }

    @Transactional(readOnly = true)
    public Reservation getInternal(UUID reservationId) {
        return required(reservationId);
    }

    @Transactional(readOnly = true)
    public Page<Reservation> customerHistory(UUID actorId, Pageable pageable) {
        return reservations.findByCustomerIdOrderByCreatedAtDesc(actorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Reservation> staffHistory(
            ReservationStatus status,
            LocalDate checkInFrom,
            LocalDate checkInTo,
            String reference,
            Pageable pageable) {
        Specification<Reservation> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (checkInFrom != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("checkIn"), checkInFrom));
        if (checkInTo != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("checkIn"), checkInTo));
        if (reference != null && !reference.isBlank()) {
            String exact = reference.trim().toUpperCase(java.util.Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("reference"), exact));
        }
        return reservations.findAll(spec, pageable);
    }

    @Transactional
    public ChangeOutcome changeForCustomer(
            UUID actorId,
            UUID reservationId,
            ReservationDtos.ChangeRequest request,
            String key) {
        Reservation reservation = required(reservationId);
        requireOwner(reservation, actorId);
        return change(actorId, reservation, request, key, false);
    }

    @Transactional
    public ChangeOutcome changeByStaff(
            UUID actorId,
            UUID reservationId,
            ReservationDtos.ChangeRequest request,
            String key) {
        return change(actorId, required(reservationId), request, key, true);
    }

    private ChangeOutcome change(
            UUID actorId,
            Reservation reservation,
            ReservationDtos.ChangeRequest request,
            String key,
            boolean staff) {
        requireKey(key);
        validateStay(request.checkIn(), request.checkOut(), request.roomCount(), request.adults(), request.children());
        String scope = "CHANGE:" + reservation.getId();
        String hash = hash(request, reservation.getId());
        Optional<IdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayChange(replay.get(), hash);
        requireVersion(reservation, request.expectedVersion());
        if (reservation.getStatus().terminal()
                || reservation.getStatus() == ReservationStatus.CHECKED_IN
                || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw ApiException.conflict("Reservation cannot be changed in status " + reservation.getStatus());
        }

        ReservationAmendment amendment = request.toAmendment();
        boolean direct = staff || reservation.canCustomerChangeDirectly(Instant.now(), properties.reservation().changeCutoff());
        if (direct) {
            applyAmendment(reservation, amendment, reservation.getStatus() == ReservationStatus.CONFIRMED);
            events.append(reservation, "reservation.changed.v1", true, Map.of("changedBy", actorId, "approved", staff));
            idempotency.save(new IdempotencyRecord(
                    key, scope, actorId, hash, "RESERVATION", reservation.getId()));
            return new ChangeOutcome(reservation, null, true);
        }

        if (changes.findByReservationIdAndStatus(reservation.getId(), ChangeRequestStatus.PENDING).isPresent()) {
            throw ApiException.conflict("This reservation already has a pending change request");
        }
        ReservationChangeRequest change = new ReservationChangeRequest(
                reservation.getId(), actorId, writeJson(amendment));
        changes.save(change);
        events.append(reservation, "reservation.change-requested.v1", false, Map.of("changeRequestId", change.getId()));
        idempotency.save(new IdempotencyRecord(
                key, scope, actorId, hash, "CHANGE_REQUEST", change.getId()));
        return new ChangeOutcome(reservation, change, false);
    }

    @Transactional
    public Reservation approveChange(
            UUID actorId,
            UUID changeRequestId,
            long expectedChangeVersion,
            String key) {
        requireKey(key);
        ReservationChangeRequest change = requiredChange(changeRequestId);
        String scope = "APPROVE_CHANGE:" + changeRequestId;
        String hash = hash(changeRequestId, expectedChangeVersion);
        Optional<IdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayReservation(replay.get(), hash);
        if (change.getVersion() != expectedChangeVersion) throw ApiException.versionConflict();

        Reservation reservation = required(change.getReservationId());
        ReservationAmendment amendment = readJson(change.getRequestedChange(), ReservationAmendment.class);
        requireVersion(reservation, amendment.expectedVersion());
        applyAmendment(reservation, amendment, reservation.getStatus() == ReservationStatus.CONFIRMED);
        change.approve(actorId);
        events.append(reservation, "reservation.changed.v1", true, Map.of("changeRequestId", change.getId(), "approved", true));
        idempotency.save(new IdempotencyRecord(
                key, scope, actorId, hash, "RESERVATION", reservation.getId()));
        return reservation;
    }

    @Transactional
    public ReservationChangeRequest rejectChange(
            UUID actorId,
            UUID changeRequestId,
            long expectedChangeVersion,
            String reason,
            String key) {
        requireKey(key);
        ReservationChangeRequest change = requiredChange(changeRequestId);
        String scope = "REJECT_CHANGE:" + changeRequestId;
        String hash = hash(changeRequestId, expectedChangeVersion, reason);
        Optional<IdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayChangeRequest(replay.get(), hash);
        if (change.getVersion() != expectedChangeVersion) throw ApiException.versionConflict();
        change.reject(actorId, reason);
        Reservation reservation = required(change.getReservationId());
        events.append(reservation, "reservation.change-rejected.v1", false, Map.of("changeRequestId", change.getId()));
        idempotency.save(new IdempotencyRecord(
                key, scope, actorId, hash, "CHANGE_REQUEST", change.getId()));
        return change;
    }

    @Transactional
    public Reservation confirm(UUID actorId, UUID reservationId, long expectedVersion, String key) {
        requireKey(key);
        Reservation reservation = required(reservationId);
        String scope = "CONFIRM:" + reservationId;
        String hash = hash(reservationId, expectedVersion);
        Optional<IdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayReservation(replay.get(), hash);
        requireVersion(reservation, expectedVersion);
        if (reservation.getStatus() != ReservationStatus.PENDING_CONFIRMATION) {
            throw ApiException.conflict("Only a pending manual reservation can be confirmed");
        }
        try {
            inventory.confirm(reservation.getInventoryHoldId(), reservationId);
            reservation.confirm();
        } catch (IllegalStateException error) {
            throw ApiException.conflict(error.getMessage());
        }
        events.append(reservation, "reservation.confirmed.v1", true, Map.of("confirmationMode", "MANUAL", "confirmedBy", actorId));
        idempotency.save(new IdempotencyRecord(key, scope, actorId, hash, "RESERVATION", reservationId));
        return reservation;
    }

    @Transactional
    public Reservation reject(UUID actorId, UUID reservationId, long expectedVersion, String reason, String key) {
        requireKey(key);
        Reservation reservation = required(reservationId);
        String scope = "REJECT:" + reservationId;
        String hash = hash(reservationId, expectedVersion, reason);
        Optional<IdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayReservation(replay.get(), hash);
        requireVersion(reservation, expectedVersion);
        if (reservation.getStatus() != ReservationStatus.PENDING_CONFIRMATION) {
            throw ApiException.conflict("Only a pending manual reservation can be rejected");
        }
        if (reason == null || reason.isBlank()) throw ApiException.validation("A rejection reason is required");
        try {
            inventory.release(reservation.getInventoryHoldId(), "reservation-rejected");
            bestEffortReleasePromotion(reservationId);
            reservation.reject(reason);
        } catch (IllegalStateException error) {
            throw ApiException.conflict(error.getMessage());
        }
        events.append(reservation, "reservation.rejected.v1", true, Map.of("rejectedBy", actorId, "reason", reason));
        idempotency.save(new IdempotencyRecord(key, scope, actorId, hash, "RESERVATION", reservationId));
        return reservation;
    }

    @Transactional
    public Reservation cancelForCustomer(
            UUID actorId, UUID reservationId, long expectedVersion, String reason, String key) {
        Reservation reservation = required(reservationId);
        requireOwner(reservation, actorId);
        return cancel(actorId, reservation, expectedVersion, reason, key);
    }

    @Transactional
    public Reservation cancelByStaff(
            UUID actorId, UUID reservationId, long expectedVersion, String reason, String key) {
        return cancel(actorId, required(reservationId), expectedVersion, reason, key);
    }

    private Reservation cancel(
            UUID actorId, Reservation reservation, long expectedVersion, String reason, String key) {
        requireKey(key);
        String scope = "CANCEL:" + reservation.getId();
        String hash = hash(reservation.getId(), expectedVersion, reason);
        Optional<IdempotencyRecord> replay = replay(key, scope, actorId);
        if (replay.isPresent()) return replayReservation(replay.get(), hash);
        requireVersion(reservation, expectedVersion);
        if (reservation.getStatus().terminal() || reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            throw ApiException.conflict("Reservation cannot be cancelled in status " + reservation.getStatus());
        }
        if (reason == null || reason.isBlank()) throw ApiException.validation("A cancellation reason is required");
        try {
            inventory.release(reservation.getInventoryHoldId(), "reservation-cancelled");
            bestEffortReleasePromotion(reservation.getId());
            BigDecimal fee = reservation.cancel(reason, Instant.now());
            events.append(reservation, "reservation.cancelled.v1", true, Map.of(
                    "cancelledBy", actorId,
                    "reason", reason,
                    "cancellationFee", fee,
                    "refundDue", reservation.getPaidAmount().subtract(fee).max(BigDecimal.ZERO)));
        } catch (IllegalStateException error) {
            throw ApiException.conflict(error.getMessage());
        }
        idempotency.save(new IdempotencyRecord(
                key, scope, actorId, hash, "RESERVATION", reservation.getId()));
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<ReservationChangeRequest> changesForStaff(UUID reservationId) {
        required(reservationId);
        return changes.findByReservationIdOrderByCreatedAtDesc(reservationId);
    }

    @Transactional
    public void expireHolds(Instant now, int batchSize) {
        List<Reservation> expired = reservations.findExpiredHoldsForUpdate(
                now,
                List.of(ReservationStatus.HOLD, ReservationStatus.PENDING_PAYMENT, ReservationStatus.PENDING_CONFIRMATION),
                org.springframework.data.domain.PageRequest.of(0, batchSize));
        for (Reservation reservation : expired) {
            try {
                reservation.expire();
                bestEffortReleasePromotion(reservation.getId());
                events.append(reservation, "reservation.hold-expired.v1", true, Map.of());
            } catch (IllegalStateException ignored) {
                // The pessimistic lock and state check make a concurrent valid transition win safely.
            }
        }
    }

    @Transactional
    public void recordPayment(
            UUID eventId,
            String eventType,
            String producer,
            String correlationId,
            UUID reservationId,
            BigDecimal amount,
            String currency) {
        // Inbox insertion and the aggregate update share this transaction.
        if (events.inboxContains(eventId)) return;
        events.startInbox(eventId, eventType, producer, reservationId, correlationId);
        Reservation reservation = required(reservationId);
        if ("payment.succeeded.v1".equals(eventType)) {
            ReservationStatus previous = reservation.getStatus();
            try {
                ReservationStatus current = reservation.recordPayment(amount, currency);
                if (previous == ReservationStatus.PENDING_PAYMENT
                        && current == ReservationStatus.CONFIRMED) {
                    inventory.confirm(reservation.getInventoryHoldId(), reservation.getId());
                    events.append(reservation, "reservation.confirmed.v1", true, Map.of("confirmationMode", "PAYMENT"));
                } else {
                    events.append(reservation, "reservation.changed.v1", true, Map.of(
                            "reason", "PAYMENT_RECEIVED",
                            "paymentAmount", amount));
                }
            } catch (IllegalArgumentException | IllegalStateException error) {
                throw ApiException.validation(error.getMessage());
            }
        } else if ("payment.failed.v1".equals(eventType)) {
            events.append(reservation, "reservation.payment-failed.v1", false, Map.of("paymentAmount", amount));
        } else if ("payment.refund-completed.v1".equals(eventType)) {
            try {
                reservation.recordRefund(amount, currency);
                events.append(
                        reservation,
                        "reservation.changed.v1",
                        true,
                        Map.of("reason", "PAYMENT_REFUNDED", "refundAmount", amount));
            } catch (IllegalArgumentException error) {
                throw ApiException.validation(error.getMessage());
            }
        }
        events.finishInbox(eventId);
    }

    @Transactional
    public void recordStayTransition(
            UUID eventId,
            String eventType,
            String producer,
            String correlationId,
            UUID reservationId) {
        if (events.inboxContains(eventId)) return;
        events.startInbox(eventId, eventType, producer, reservationId, correlationId);
        Reservation reservation = required(reservationId);
        try {
            switch (eventType) {
                case "stay.checked-in.v1" -> reservation.checkIn();
                case "stay.checked-out.v1" -> {
                    reservation.checkOut();
                    inventory.release(reservation.getInventoryHoldId(), "STAY_CHECKED_OUT");
                }
                case "stay.no-show.v1" -> {
                    reservation.noShow();
                    inventory.release(reservation.getInventoryHoldId(), "STAY_NO_SHOW");
                }
                default -> throw new IllegalArgumentException("Unsupported stay event " + eventType);
            }
        } catch (IllegalStateException error) {
            throw ApiException.conflict(error.getMessage());
        }
        events.append(reservation, "reservation.changed.v1", true, Map.of("reason", eventType));
        events.finishInbox(eventId);
    }

    private void applyAmendment(Reservation reservation, ReservationAmendment amendment, boolean preserveConfirmation) {
        PricingSnapshot quote = pricing.quote(new PricingGateway.QuoteCommand(
                reservation.getCustomerId(), amendment.roomTypeId(), amendment.checkIn(), amendment.checkOut(),
                amendment.roomCount(), amendment.adults(), amendment.children(), amendment.promotionCode(),
                reservation.getChannel().name()));
        validateQuote(quote);
        UUID previousQuoteId = reservation.getPricingQuoteId();
        UUID previousHoldId = reservation.getInventoryHoldId();
        boolean allocationChanged = !reservation.getRoomTypeId().equals(amendment.roomTypeId())
                || !reservation.getCheckIn().equals(amendment.checkIn())
                || !reservation.getCheckOut().equals(amendment.checkOut())
                || reservation.getRoomCount() != amendment.roomCount();
        InventoryHold hold = allocationChanged
                ? inventory.acquire(
                        new InventoryGateway.HoldCommand(
                                reservation.getId(), amendment.roomTypeId(), amendment.checkIn(), amendment.checkOut(),
                                amendment.roomCount(), Instant.now().plus(properties.reservation().holdDuration())),
                        "reservation-change:" + reservation.getId() + ":"
                                + amendment.expectedVersion() + ":" + UUID.randomUUID())
                : new InventoryHold(
                        reservation.getInventoryHoldId(),
                        reservation.getHoldExpiresAt(),
                        reservation.getInventoryHoldVersion());
        try {
            pricing.synchronizePromotion(
                    reservation.getId(),
                    quote.quoteId(),
                    reservation.getCustomerId(),
                    false,
                    promotionCommandKey("change"));
            reservation.applyChange(
                    amendment.roomTypeId(), amendment.checkIn(), amendment.checkOut(), amendment.roomCount(),
                    amendment.adults(), amendment.children(), amendment.specialRequests(), hold, quote,
                    preserveConfirmation);
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                if (allocationChanged) {
                    inventory.replace(previousHoldId, hold.holdId(), reservation.getId());
                } else {
                    inventory.confirm(hold.holdId(), reservation.getId());
                }
            } else if (allocationChanged) {
                inventory.release(previousHoldId, "reservation-allocation-replaced");
            }
        } catch (IllegalStateException error) {
            if (allocationChanged) bestEffortRelease(hold.holdId(), "reservation-change-failed");
            bestEffortRestorePromotion(reservation, previousQuoteId);
            throw ApiException.conflict(error.getMessage());
        } catch (RuntimeException error) {
            if (allocationChanged) bestEffortRelease(hold.holdId(), "reservation-change-failed");
            bestEffortRestorePromotion(reservation, previousQuoteId);
            throw error;
        }
    }

    private void validateStay(LocalDate checkIn, LocalDate checkOut, int rooms, int adults, int children) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw ApiException.validation("checkOut must be after checkIn");
        }
        if (checkIn.isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw ApiException.validation("checkIn cannot be in the past");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > properties.reservation().maximumStayNights()) {
            throw ApiException.validation("Stay exceeds the configured maximum number of nights");
        }
        if (rooms < 1 || adults < 1 || children < 0) {
            throw ApiException.validation("roomCount and adults must be positive; children cannot be negative");
        }
    }

    private static void validateGuests(List<ReservationDtos.GuestRequest> guests) {
        if (guests == null || guests.isEmpty()) return;
        long primary = guests.stream().filter(ReservationDtos.GuestRequest::primary).count();
        if (primary != 1) throw ApiException.validation("Exactly one guest must be marked as primary");
    }

    private static void validateQuote(PricingSnapshot quote) {
        if (quote.quoteId() == null || quote.pricingVersion() == null || quote.pricingVersion().isBlank()) {
            throw ApiException.upstream("Pricing quote is missing identity or version");
        }
        if (quote.quoteExpiresAt() == null || !quote.quoteExpiresAt().isAfter(Instant.now())) {
            throw ApiException.conflict("Pricing quote has expired; request a new quote");
        }
        if (quote.currency() == null || !quote.currency().matches("[A-Z]{3}")) {
            throw ApiException.upstream("Pricing quote currency is invalid");
        }
        if (quote.subtotal() == null || quote.totalAmount() == null || quote.subtotal().signum() < 0
                || quote.totalAmount().signum() < 0 || quote.depositRequired().signum() < 0
                || quote.depositRequired().compareTo(quote.totalAmount()) > 0) {
            throw ApiException.upstream("Pricing quote amounts are invalid");
        }
    }

    private static void addGuests(Reservation reservation, ReservationDtos.CreateRequest request) {
        if (request.guests() == null || request.guests().isEmpty()) {
            reservation.addGuest(request.contactName(), null, null, null, true);
            return;
        }
        request.guests().forEach(guest -> reservation.addGuest(
                guest.fullName(), guest.documentType(), guest.documentNumber(), guest.nationality(), guest.primary()));
    }

    private Reservation detailed(UUID id) {
        return reservations.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    private Reservation required(UUID id) {
        return reservations.findById(id).orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    private ReservationChangeRequest requiredChange(UUID id) {
        return changes.findById(id).orElseThrow(() -> ApiException.notFound("Reservation change request not found"));
    }

    private static void requireOwner(Reservation reservation, UUID actorId) {
        if (!reservation.getCustomerId().equals(actorId)) throw ApiException.notFound("Reservation not found");
    }

    private static void requireVersion(Reservation reservation, long expectedVersion) {
        if (reservation.getVersion() != expectedVersion) throw ApiException.versionConflict();
    }

    private Optional<IdempotencyRecord> replay(String key, String scope, UUID actorId) {
        idempotency.lockScopeKey(actorId + "|" + scope + "|" + key);
        return idempotency.findByKeyAndScopeAndActorId(key, scope, actorId);
    }

    private Reservation replayReservation(IdempotencyRecord record, String hash) {
        requireSameRequest(record, hash);
        if (!"RESERVATION".equals(record.getResourceType())) throw ApiException.conflict("Idempotency key has a different result type");
        return required(record.getResourceId());
    }

    private ChangeOutcome replayChange(IdempotencyRecord record, String hash) {
        requireSameRequest(record, hash);
        if ("RESERVATION".equals(record.getResourceType())) {
            return new ChangeOutcome(required(record.getResourceId()), null, true);
        }
        ReservationChangeRequest change = requiredChange(record.getResourceId());
        return new ChangeOutcome(required(change.getReservationId()), change, false);
    }

    private ReservationChangeRequest replayChangeRequest(IdempotencyRecord record, String hash) {
        requireSameRequest(record, hash);
        if (!"CHANGE_REQUEST".equals(record.getResourceType())) throw ApiException.conflict("Idempotency key has a different result type");
        return requiredChange(record.getResourceId());
    }

    private static void requireSameRequest(IdempotencyRecord record, String hash) {
        if (!MessageDigest.isEqual(
                record.getRequestHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII))) {
            throw ApiException.conflict("Idempotency-Key was already used with a different request");
        }
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw ApiException.validation("A valid Idempotency-Key header (8-160 safe characters) is required");
        }
    }

    private String hash(Object... values) {
        try {
            byte[] encoded = objectMapper.writeValueAsBytes(values);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash idempotent request", error);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize reservation change", error);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot deserialize reservation change", error);
        }
    }

    private void bestEffortRelease(UUID holdId, String reason) {
        try {
            inventory.release(holdId, reason);
        } catch (RuntimeException ignored) {
            // Holds have a short expiry; failure here must not hide the original error.
        }
    }

    private void bestEffortReleasePromotion(UUID reservationId) {
        try {
            pricing.releasePromotion(reservationId, promotionCommandKey("release"));
        } catch (RuntimeException ignored) {
            // Reservation events provide the audit trail; a later retry/reconciliation can release it.
        }
    }

    private void bestEffortRestorePromotion(Reservation reservation, UUID previousQuoteId) {
        try {
            pricing.synchronizePromotion(
                    reservation.getId(),
                    previousQuoteId,
                    reservation.getCustomerId(),
                    true,
                    promotionCommandKey("restore"));
        } catch (RuntimeException ignored) {
            // Do not hide the original failure; reconciliation can restore the previous snapshot.
        }
    }

    private static String promotionCommandKey(String operation) {
        return "reservation-promotion-" + operation + ":" + UUID.randomUUID();
    }

    private static String reference(UUID id) {
        return "RSV" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "")
                + id.toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ChangeOutcome(Reservation reservation, ReservationChangeRequest changeRequest, boolean applied) {}
}
