package com.hotelai.stay.domain;

import com.hotelai.stay.common.ApiException;
import com.hotelai.stay.config.StayProperties;
import com.hotelai.stay.domain.StayCommands.ChangeRoom;
import com.hotelai.stay.domain.StayCommands.CheckIn;
import com.hotelai.stay.domain.StayCommands.Checkout;
import com.hotelai.stay.domain.StayCommands.Extend;
import com.hotelai.stay.domain.StayCommands.Guest;
import com.hotelai.stay.domain.StayCommands.Incidental;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class StayApplicationService {
    private final ReservationStayProjectionRepository reservations;
    private final RoomTypeProjectionRepository roomTypes;
    private final RoomProjectionRepository rooms;
    private final StayRepository stays;
    private final StayGuestRepository guests;
    private final RoomAssignmentRepository assignments;
    private final StayFolioEntryRepository folio;
    private final StayOperationRepository operations;
    private final SensitiveDocumentCipher documents;
    private final InventoryStayClient inventory;
    private final StayEventService events;
    private final StayProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public StayApplicationService(
            ReservationStayProjectionRepository reservations,
            RoomTypeProjectionRepository roomTypes,
            RoomProjectionRepository rooms,
            StayRepository stays,
            StayGuestRepository guests,
            RoomAssignmentRepository assignments,
            StayFolioEntryRepository folio,
            StayOperationRepository operations,
            SensitiveDocumentCipher documents,
            InventoryStayClient inventory,
            StayEventService events,
            StayProperties properties,
            Clock clock,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.reservations = reservations;
        this.roomTypes = roomTypes;
        this.rooms = rooms;
        this.stays = stays;
        this.guests = guests;
        this.assignments = assignments;
        this.folio = folio;
        this.operations = operations;
        this.documents = documents;
        this.inventory = inventory;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public StayDetails checkIn(UUID actorId, CheckIn command, String key) {
        requireKey(key);
        String hash = hash(command);
        UUID stayId = transaction.execute(status -> {
            Stay existing = stays.findByReservationId(command.reservationId()).orElse(null);
            if (existing != null) {
                if (!existing.getCheckInRequestHash().equals(hash)) {
                    throw ApiException.conflict(
                            "check-in-already-exists",
                            "Reservation already has a check-in with different data");
                }
                return existing.getId();
            }
            ReservationStayProjection reservation = requiredReservation(command.reservationId());
            validateCheckInWindow(reservation, command.earlyCheckInApproved());
            List<RoomProjection> selectedRooms = requiredRooms(command.roomIds());
            validateCheckInRooms(reservation, selectedRooms);
            validateGuests(reservation, command.guests());

            Stay stay = Stay.pendingCheckIn(
                    reservation, actorId, hash, command.outstandingDepositAcknowledged());
            stays.save(stay);
            for (Guest guest : command.guests()) {
                guests.save(new StayGuest(
                        stay.getId(),
                        guest.fullName(),
                        guest.dateOfBirth(),
                        guest.nationality(),
                        guest.documentType(),
                        documents.encrypt(guest.documentNumber()),
                        SensitiveDocumentCipher.last4(guest.documentNumber()),
                        guest.primaryGuest()));
            }
            for (RoomProjection room : selectedRooms) {
                assignments.save(RoomAssignment.planned(
                        stay.getId(), room, actorId, "CHECK_IN"));
            }
            return stay.getId();
        });
        advanceCheckIn(stayId);
        return details(stayId);
    }

    public StayDetails changeRoom(
            UUID actorId, UUID stayId, ChangeRoom command, String key) {
        requireKey(key);
        String hash = hash(command);
        UUID operationId = transaction.execute(status -> {
            Stay stay = requiredLockedStay(stayId);
            requireOpen(stay);
            StayOperation replay = operations.findByStayIdAndTypeAndIdempotencyKey(
                    stayId, OperationType.ROOM_CHANGE, key).orElse(null);
            if (replay != null) return replayOperation(replay, hash).getId();

            RoomAssignment current = assignments.findByStayIdAndRoomIdAndStatus(
                            stayId, command.fromRoomId(), AssignmentStatus.ACTIVE)
                    .orElseThrow(() -> ApiException.conflict(
                            "room-not-assigned", "Source room is not assigned to this stay"));
            RoomProjection target = rooms.findById(command.targetRoomId())
                    .orElseThrow(() -> ApiException.notFound("Target room not found"));
            if (!target.readyFor(stayId)) {
                throw ApiException.conflict(
                        "target-room-not-ready", "Target room is not ready for assignment");
            }
            if (assignments.findByStayIdAndRoomIdAndStatus(
                    stayId, target.getId(), AssignmentStatus.ACTIVE).isPresent()) {
                throw ApiException.conflict(
                        "target-room-already-assigned", "Target room is already assigned to this stay");
            }
            requireRoomCapacity(target, stay.getAdults(), stay.getChildren(),
                    Math.max(1, activeAssignments(stayId).size()));
            StayOperation operation = StayOperation.roomChange(
                    stayId, key, hash, current.getRoomId(), target.getId(),
                    command.priceDelta(), command.reason(), actorId);
            operations.save(operation);
            assignments.save(RoomAssignment.planned(
                    stayId, target, actorId, "ROOM_CHANGE:" + operation.getId()));
            return operation.getId();
        });
        advanceOperation(operationId);
        return details(stayId);
    }

    public StayDetails extend(UUID actorId, UUID stayId, Extend command, String key) {
        requireKey(key);
        String hash = hash(command);
        UUID operationId = transaction.execute(status -> {
            Stay stay = requiredLockedStay(stayId);
            requireOpen(stay);
            StayOperation replay = operations.findByStayIdAndTypeAndIdempotencyKey(
                    stayId, OperationType.EXTENSION, key).orElse(null);
            if (replay != null) return replayOperation(replay, hash).getId();
            if (command.newCheckoutDate() == null
                    || !command.newCheckoutDate().isAfter(stay.getScheduledCheckOut())) {
                throw ApiException.validation("New checkout date must be after current checkout");
            }
            if (java.time.temporal.ChronoUnit.DAYS.between(
                    stay.getScheduledCheckOut(), command.newCheckoutDate())
                    > properties.stay().maximumExtensionDays()) {
                throw ApiException.validation("Extension exceeds the configured maximum");
            }
            StayOperation operation = StayOperation.extension(
                    stayId, key, hash, stay.getScheduledCheckOut(),
                    command.newCheckoutDate(), command.priceDelta(), command.reason(), actorId);
            operations.save(operation);
            return operation.getId();
        });
        advanceOperation(operationId);
        return details(stayId);
    }

    public StayDetails checkout(
            UUID actorId, boolean manager, UUID stayId, Checkout command, String key) {
        requireKey(key);
        String hash = hash(command);
        UUID operationId = transaction.execute(status -> {
            Stay stay = requiredLockedStay(stayId);
            if (stay.getStatus() != StayStatus.OPEN
                    && stay.getStatus() != StayStatus.CHECKOUT_PAYMENT_PENDING
                    && stay.getStatus() != StayStatus.CHECKOUT_PENDING) {
                throw ApiException.conflict(
                        "stay-not-open", "Stay cannot be checked out from " + stay.getStatus());
            }
            StayOperation replay = operations.findByStayIdAndTypeAndIdempotencyKey(
                    stayId, OperationType.CHECKOUT, key).orElse(null);
            if (replay != null) return replayOperation(replay, hash).getId();

            if (command.debtOverride() && !manager) {
                throw ApiException.forbidden("Only a manager can approve checkout with a balance");
            }
            if (command.debtOverride()
                    && (command.debtOverrideReason() == null
                        || command.debtOverrideReason().isBlank())) {
                throw ApiException.validation("Debt override reason is required");
            }
            StayOperation operation = operations.save(
                    StayOperation.checkout(stayId, key, hash, actorId));
            List<Incidental> items =
                    command.incidentals() == null ? List.of() : command.incidentals();
            for (Incidental item : items) {
                StayFolioEntry entry = new StayFolioEntry(
                        stayId, operation.getId(), item.type(), item.description(),
                        item.amount(), stay.getCurrency(), actorId);
                folio.save(entry);
                stay.addCharge(entry.getAmount());
            }
            if (stay.balance().signum() == 0 || command.debtOverride()) {
                stay.beginCheckout(
                        command.debtOverride() ? actorId : null,
                        command.debtOverrideReason());
                operation.releasePending();
            } else {
                stay.markCheckoutPaymentPending();
                operation.paymentPending();
                events.append(stay, "stay.checkout-payment-pending.v1",
                        activeAssignments(stayId), operation);
            }
            return operation.getId();
        });
        advanceOperation(operationId);
        return details(stayId);
    }

    public StayDetails continueCheckout(
            UUID actorId, boolean manager, UUID stayId, String overrideReason) {
        UUID operationId = transaction.execute(status -> {
            Stay stay = requiredLockedStay(stayId);
            StayOperation operation = operations.findFirstByStayIdAndTypeOrderByCreatedAtDesc(
                            stayId, OperationType.CHECKOUT)
                    .orElseThrow(() -> ApiException.notFound("Checkout operation not found"));
            if (operation.getStatus() == OperationStatus.COMPLETED) return operation.getId();
            if (operation.getStatus() == OperationStatus.RELEASE_PENDING) {
                return operation.getId();
            }
            if (stay.getStatus() == StayStatus.CHECKOUT_PENDING) {
                // Recover records created before checkout retained the managed
                // instance returned by JpaRepository.save(). The Stay already
                // contains the manager approval, so only the operation needs
                // to enter the room-release phase.
                operation.releasePending();
                return operation.getId();
            }
            if (stay.balance().signum() != 0) {
                if (!manager) {
                    throw ApiException.conflict(
                            "outstanding-balance", "Outstanding balance must be settled");
                }
                if (overrideReason == null || overrideReason.isBlank()) {
                    throw ApiException.validation("Manager override reason is required");
                }
                stay.beginCheckout(actorId, overrideReason);
            } else {
                stay.beginCheckout(null, null);
            }
            operation.releasePending();
            return operation.getId();
        });
        advanceOperation(operationId);
        return details(stayId);
    }

    public void advancePending(int batchSize) {
        stays.findByStatusOrderByUpdatedAtAsc(
                        StayStatus.CHECK_IN_PENDING, PageRequest.of(0, batchSize))
                .forEach(stay -> advanceCheckIn(stay.getId()));
        operations.findPending(PageRequest.of(0, batchSize))
                .forEach(operation -> advanceOperation(operation.getId()));
        operations.findCompletedWithMissingFolio(PageRequest.of(0, batchSize))
                .forEach(operation -> repairMissingOperationFolio(operation.getId()));
    }

    private void repairMissingOperationFolio(UUID operationId) {
        transaction.executeWithoutResult(status -> {
            StayOperation operation = requiredLockedOperation(operationId);
            if (operation.getStatus() != OperationStatus.COMPLETED
                    || operation.getAmountDelta() == null
                    || operation.getAmountDelta().signum() == 0
                    || folio.existsByOperationId(operationId)) {
                return;
            }
            Stay stay = requiredLockedStay(operation.getStayId());
            recordOperationCharge(stay, operation);
            events.append(
                    stay,
                    "stay.folio-updated.v1",
                    assignments.findByStayIdOrderByCreatedAtAsc(stay.getId()),
                    operation);
        });
    }

    private void recordOperationCharge(Stay stay, StayOperation operation) {
        BigDecimal amount = operation.getAmountDelta();
        if (amount == null || amount.signum() == 0
                || folio.existsByOperationId(operation.getId())) {
            return;
        }
        String entryType = operation.getType() == OperationType.EXTENSION
                ? "EXTENSION" : "ROOM_CHANGE";
        String description = operation.getType() == OperationType.EXTENSION
                ? "Stay extension to " + operation.getRequestedCheckoutDate()
                : "Room change price adjustment";
        folio.save(new StayFolioEntry(
                stay.getId(), operation.getId(), entryType, description,
                amount, stay.getCurrency(), operation.getRequestedBy()));
    }

    public List<ReservationSummary> searchReservations(String query) {
        String clean = query == null ? "" : query.trim();
        return reservations.search(
                        List.of("CONFIRMED", "CHECKED_IN"), clean, PageRequest.of(0, 50))
                .stream()
                .map(value -> new ReservationSummary(
                        value.getReservationId(), value.getReference(), value.getCustomerId(),
                        value.getContactName(), value.getContactPhone(), value.getStatus(),
                        value.getCheckIn(), value.getCheckOut(), value.getRoomCount(),
                        value.getAdults(), value.getChildren(), value.getCurrency(),
                        value.getTotalAmount(), value.getDepositRequired(), value.getPaidAmount()))
                .toList();
    }

    public StayDetails details(UUID stayId) {
        Stay stay = stays.findById(stayId)
                .orElseThrow(() -> ApiException.notFound("Stay not found"));
        return new StayDetails(
                stay,
                guests.findByStayIdOrderByPrimaryGuestDescCreatedAtAsc(stayId),
                assignments.findByStayIdOrderByCreatedAtAsc(stayId),
                folio.findByStayIdOrderByRecordedAtAsc(stayId),
                operations.findByStayIdOrderByCreatedAtAsc(stayId));
    }

    private void advanceCheckIn(UUID stayId) {
        Stay stay = stays.findById(stayId).orElse(null);
        if (stay == null || stay.getStatus() != StayStatus.CHECK_IN_PENDING) return;
        try {
            List<RoomAssignment> planned = assignments.findByStayIdAndStatusOrderByCreatedAtAsc(
                    stayId, AssignmentStatus.PLANNED);
            for (RoomAssignment assignment : planned) {
                inventory.occupy(
                        assignment.getRoomId(), stayId,
                        "Check-in " + stay.getReservationReference());
            }
            transaction.executeWithoutResult(status -> {
                Stay locked = requiredLockedStay(stayId);
                if (locked.getStatus() != StayStatus.CHECK_IN_PENDING) return;
                List<RoomAssignment> current =
                        assignments.findByStayIdAndStatusOrderByCreatedAtAsc(
                                stayId, AssignmentStatus.PLANNED);
                Instant now = clock.instant();
                current.forEach(assignment -> assignment.activate(now));
                locked.open(now);
                events.append(locked, "stay.checked-in.v1", activeAssignments(stayId), null);
            });
        } catch (RuntimeException error) {
            recordStaySagaError(stayId, error);
        }
    }

    private void advanceOperation(UUID operationId) {
        StayOperation snapshot = operations.findById(operationId).orElse(null);
        if (snapshot == null || snapshot.getStatus() == OperationStatus.COMPLETED) return;
        try {
            if (snapshot.getType() == OperationType.ROOM_CHANGE) {
                advanceRoomChange(snapshot);
            } else if (snapshot.getType() == OperationType.EXTENSION) {
                advanceExtension(snapshot);
            } else if (snapshot.getType() == OperationType.CHECKOUT) {
                advanceCheckout(snapshot);
            }
        } catch (RuntimeException error) {
            recordOperationError(operationId, error);
        }
    }

    private void advanceRoomChange(StayOperation snapshot) {
        UUID operationId = snapshot.getId();
        if (snapshot.getStatus() == OperationStatus.PENDING_TARGET) {
            Stay stay = stays.findById(snapshot.getStayId()).orElseThrow();
            RoomAssignment oldAssignment = assignments.findByStayIdAndRoomIdAndStatus(
                            snapshot.getStayId(), snapshot.getFromRoomId(), AssignmentStatus.ACTIVE)
                    .orElseThrow();
            RoomAssignment targetAssignment = assignments.findByStayIdAndRoomIdAndStatus(
                            snapshot.getStayId(), snapshot.getTargetRoomId(), AssignmentStatus.PLANNED)
                    .orElseThrow();
            if (!oldAssignment.getRoomTypeId().equals(targetAssignment.getRoomTypeId())) {
                inventory.ensureExtensionHold(
                        requiredReservation(stay.getReservationId()).getInventoryHoldId(),
                        snapshot.getId(),
                        stay.getReservationId(),
                        targetAssignment.getRoomTypeId(),
                        LocalDate.now(clock),
                        stay.getScheduledCheckOut(),
                        1);
            }
            inventory.occupy(
                    snapshot.getTargetRoomId(), snapshot.getStayId(),
                    "Room change " + snapshot.getId());
            transaction.executeWithoutResult(status -> {
                StayOperation locked = requiredLockedOperation(operationId);
                if (locked.getStatus() == OperationStatus.PENDING_TARGET) locked.targetOccupied();
            });
            snapshot = operations.findById(operationId).orElseThrow();
        }
        if (snapshot.getStatus() == OperationStatus.TARGET_OCCUPIED) {
            transaction.executeWithoutResult(status -> {
                StayOperation locked = requiredLockedOperation(operationId);
                if (locked.getStatus() != OperationStatus.TARGET_OCCUPIED) return;
                Stay stay = requiredLockedStay(locked.getStayId());
                RoomAssignment oldAssignment = assignments.findByStayIdAndRoomIdAndStatus(
                                stay.getId(), locked.getFromRoomId(), AssignmentStatus.ACTIVE)
                        .orElseThrow();
                RoomAssignment newAssignment = assignments.findByStayIdAndRoomIdAndStatus(
                                stay.getId(), locked.getTargetRoomId(), AssignmentStatus.PLANNED)
                        .orElseThrow();
                Instant now = clock.instant();
                newAssignment.activate(now);
                oldAssignment.end(now);
                stay.addCharge(locked.getAmountDelta());
                recordOperationCharge(stay, locked);
                locked.releasePending();
            });
            snapshot = operations.findById(operationId).orElseThrow();
        }
        if (snapshot.getStatus() == OperationStatus.RELEASE_PENDING) {
            inventory.release(
                    snapshot.getFromRoomId(), snapshot.getStayId(),
                    "Room changed by operation " + snapshot.getId());
            transaction.executeWithoutResult(status -> {
                StayOperation locked = requiredLockedOperation(operationId);
                if (locked.getStatus() != OperationStatus.RELEASE_PENDING) return;
                locked.complete();
                Stay stay = requiredLockedStay(locked.getStayId());
                stay.clearSagaError();
                events.append(stay, "stay.room-changed.v1",
                        activeAssignments(stay.getId()), locked);
            });
        }
    }

    private void advanceExtension(StayOperation snapshot) {
        if (snapshot.getStatus() != OperationStatus.PENDING_HOLD) return;
        Stay stay = stays.findById(snapshot.getStayId()).orElseThrow();
        UUID previousHoldId = requiredReservation(stay.getReservationId()).getInventoryHoldId();
        Map<UUID, Long> counts = activeAssignments(stay.getId()).stream()
                .collect(Collectors.groupingBy(
                        RoomAssignment::getRoomTypeId, LinkedHashMap::new, Collectors.counting()));
        for (Map.Entry<UUID, Long> entry : counts.entrySet()) {
            inventory.ensureExtensionHold(
                    previousHoldId,
                    snapshot.getId(), stay.getReservationId(), entry.getKey(),
                    snapshot.getPreviousCheckoutDate(), snapshot.getRequestedCheckoutDate(),
                    entry.getValue().intValue());
        }
        transaction.executeWithoutResult(status -> {
            StayOperation locked = requiredLockedOperation(snapshot.getId());
            if (locked.getStatus() != OperationStatus.PENDING_HOLD) return;
            Stay current = requiredLockedStay(locked.getStayId());
            current.extendTo(locked.getRequestedCheckoutDate(), locked.getAmountDelta());
            recordOperationCharge(current, locked);
            current.clearSagaError();
            locked.complete();
            events.append(current, "stay.extended.v1", activeAssignments(current.getId()), locked);
        });
    }

    private void advanceCheckout(StayOperation snapshot) {
        UUID operationId = snapshot.getId();
        if (snapshot.getStatus() == OperationStatus.PAYMENT_PENDING) {
            transaction.executeWithoutResult(status -> {
                StayOperation locked = requiredLockedOperation(operationId);
                Stay stay = requiredLockedStay(locked.getStayId());
                if (locked.getStatus() == OperationStatus.PAYMENT_PENDING
                        && stay.balance().signum() == 0) {
                    stay.beginCheckout(null, null);
                    locked.releasePending();
                }
            });
            snapshot = operations.findById(operationId).orElseThrow();
        }
        if (snapshot.getStatus() != OperationStatus.RELEASE_PENDING) return;
        List<RoomAssignment> active = activeAssignments(snapshot.getStayId());
        for (RoomAssignment assignment : active) {
            inventory.release(
                    assignment.getRoomId(), snapshot.getStayId(),
                    "Checkout " + snapshot.getId());
        }
        transaction.executeWithoutResult(status -> {
            StayOperation locked = requiredLockedOperation(operationId);
            if (locked.getStatus() != OperationStatus.RELEASE_PENDING) return;
            Stay stay = requiredLockedStay(locked.getStayId());
            List<RoomAssignment> current = activeAssignments(stay.getId());
            Instant now = clock.instant();
            current.forEach(assignment -> assignment.end(now));
            stay.close(now);
            locked.complete();
            events.append(stay, "stay.checked-out.v1", current, locked);
        });
    }

    private void validateCheckInWindow(
            ReservationStayProjection reservation, boolean earlyApproved) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        if (today.isAfter(reservation.getCheckOut())
                || today.isEqual(reservation.getCheckOut())) {
            throw ApiException.conflict(
                    "check-in-window-closed", "Reservation checkout date has already arrived");
        }
        boolean earlyDate = today.isBefore(reservation.getCheckIn());
        boolean earlyHour = today.isEqual(reservation.getCheckIn())
                && now.getHour() < properties.stay().standardCheckInHour();
        if ((earlyDate || earlyHour) && !earlyApproved) {
            throw ApiException.conflict(
                    "early-check-in-approval-required",
                    "Early check-in must be approved according to hotel policy");
        }
    }

    private void validateCheckInRooms(
            ReservationStayProjection reservation, List<RoomProjection> selected) {
        if (selected.size() != reservation.getRoomCount()) {
            throw ApiException.validation("Selected room count must match reservation room count");
        }
        for (RoomProjection room : selected) {
            if (!room.readyFor(null)) {
                throw ApiException.conflict(
                        "room-not-ready", "Room " + room.getRoomNumber() + " is not ready");
            }
            if (!reservation.getRoomTypeId().equals(room.getRoomTypeId())) {
                throw ApiException.conflict(
                        "room-type-mismatch", "Selected room type does not match reservation");
            }
        }
        requireRoomCapacity(
                selected.getFirst(), reservation.getAdults(), reservation.getChildren(), selected.size());
    }

    private void requireRoomCapacity(
            RoomProjection room, int adults, int children, int roomCount) {
        RoomTypeProjection type = roomTypes.findById(room.getRoomTypeId())
                .orElseThrow(() -> ApiException.conflict(
                        "room-type-projection-missing", "Room type data is not synchronized"));
        if (!type.isActive()
                || adults > type.getMaxAdults() * roomCount
                || children > type.getMaxChildren() * roomCount
                || adults + children > type.getMaxOccupancy() * roomCount) {
            throw ApiException.conflict(
                    "room-capacity-exceeded", "Guest count exceeds selected room capacity");
        }
    }

    private void validateGuests(ReservationStayProjection reservation, List<Guest> values) {
        if (values == null || values.size() != reservation.getAdults() + reservation.getChildren()) {
            throw ApiException.validation("Guest list must match reservation occupancy");
        }
        LocalDate today = LocalDate.now(clock);
        int adults = 0;
        int children = 0;
        int primary = 0;
        for (Guest guest : values) {
            if (guest.dateOfBirth() == null || guest.dateOfBirth().isAfter(today)) {
                throw ApiException.validation("Every guest must have a valid date of birth");
            }
            int age = Period.between(guest.dateOfBirth(), today).getYears();
            if (age <= properties.stay().childMaximumAge()) children++;
            else adults++;
            if (guest.primaryGuest()) {
                primary++;
                if (age < properties.stay().primaryGuestMinimumAge()) {
                    throw ApiException.validation("Primary guest does not meet minimum age");
                }
            }
        }
        if (primary != 1) throw ApiException.validation("Exactly one primary guest is required");
        if (adults != reservation.getAdults() || children != reservation.getChildren()) {
            throw ApiException.validation("Guest ages do not match adult/child counts");
        }
    }

    private List<RoomProjection> requiredRooms(List<UUID> ids) {
        if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.validation("Room ids must be non-empty and unique");
        }
        List<RoomProjection> values = new ArrayList<>();
        for (UUID id : ids) {
            values.add(rooms.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Room not found: " + id)));
        }
        return values;
    }

    private List<RoomAssignment> activeAssignments(UUID stayId) {
        return assignments.findByStayIdAndStatusOrderByCreatedAtAsc(
                stayId, AssignmentStatus.ACTIVE);
    }

    private ReservationStayProjection requiredReservation(UUID id) {
        return reservations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reservation projection not found"));
    }

    private Stay requiredLockedStay(UUID id) {
        return stays.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound("Stay not found"));
    }

    private StayOperation requiredLockedOperation(UUID id) {
        return operations.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound("Stay operation not found"));
    }

    private static void requireOpen(Stay stay) {
        if (stay.getStatus() != StayStatus.OPEN) {
            throw ApiException.conflict("stay-not-open", "Stay is not open");
        }
    }

    private static StayOperation replayOperation(StayOperation operation, String hash) {
        if (!operation.getRequestHash().equals(hash)) {
            throw ApiException.conflict(
                    "idempotency-key-reused",
                    "Idempotency-Key was already used with different command data");
        }
        return operation;
    }

    private void recordStaySagaError(UUID stayId, RuntimeException error) {
        transaction.executeWithoutResult(status ->
                stays.findLockedById(stayId).ifPresent(stay -> stay.sagaFailed(safeMessage(error))));
    }

    private void recordOperationError(UUID operationId, RuntimeException error) {
        transaction.executeWithoutResult(status -> {
            StayOperation operation = operations.findLockedById(operationId).orElse(null);
            if (operation != null && operation.getStatus() != OperationStatus.COMPLETED) {
                operation.failed(safeMessage(error));
                stays.findLockedById(operation.getStayId())
                        .ifPresent(stay -> stay.sagaFailed(safeMessage(error)));
            }
        });
    }

    private String hash(Object command) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(command);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash stay command", error);
        }
    }

    private static String safeMessage(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw ApiException.validation("A valid Idempotency-Key is required");
        }
    }

    public record ReservationSummary(
            UUID reservationId,
            String reference,
            UUID customerId,
            String contactName,
            String contactPhone,
            String status,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomCount,
            int adults,
            int children,
            String currency,
            BigDecimal totalAmount,
            BigDecimal depositRequired,
            BigDecimal paidAmount) {}

    public record StayDetails(
            Stay stay,
            List<StayGuest> guests,
            List<RoomAssignment> assignments,
            List<StayFolioEntry> folio,
            List<StayOperation> operations) {}
}
