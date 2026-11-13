package com.hotelai.stay.domain;

import com.hotelai.stay.domain.ReservationStayProjection.ReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionService {
    private final ReservationStayProjectionRepository reservations;
    private final RoomTypeProjectionRepository roomTypes;
    private final RoomProjectionRepository rooms;
    private final ProcessedEventRepository processed;
    private final StayRepository stays;
    private final RoomAssignmentRepository assignments;
    private final StayFolioEntryRepository folio;
    private final StayEventService events;

    public ProjectionService(
            ReservationStayProjectionRepository reservations,
            RoomTypeProjectionRepository roomTypes,
            RoomProjectionRepository rooms,
            ProcessedEventRepository processed,
            StayRepository stays,
            RoomAssignmentRepository assignments,
            StayFolioEntryRepository folio,
            StayEventService events) {
        this.reservations = reservations;
        this.roomTypes = roomTypes;
        this.rooms = rooms;
        this.processed = processed;
        this.stays = stays;
        this.assignments = assignments;
        this.folio = folio;
        this.events = events;
    }

    @Transactional
    public void reservation(
            UUID eventId, String eventType, String producer, Instant occurredAt,
            ReservationSnapshot snapshot) {
        if (processed.existsById(eventId)) return;
        ReservationStayProjection projection = reservations.findById(snapshot.reservationId())
                .orElseGet(() -> new ReservationStayProjection(snapshot, occurredAt));
        projection.synchronize(snapshot, occurredAt);
        reservations.save(projection);
        stays.findByReservationId(snapshot.reservationId()).ifPresent(stay -> {
            if (stay.synchronizePaid(snapshot.paidAmount())) {
                events.append(
                        stay,
                        "stay.folio-updated.v1",
                        assignments.findByStayIdAndStatusOrderByCreatedAtAsc(
                                stay.getId(), AssignmentStatus.ACTIVE),
                        null);
            }
        });
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void roomType(
            UUID eventId, String eventType, String producer, Instant occurredAt,
            UUID roomTypeId, String code, String name, int maxAdults, int maxChildren,
            int maxOccupancy, boolean active, long version) {
        if (processed.existsById(eventId)) return;
        RoomTypeProjection value = roomTypes.findById(roomTypeId).orElseGet(RoomTypeProjection::new);
        value.synchronize(
                roomTypeId, code, name, maxAdults, maxChildren,
                maxOccupancy, active, version, occurredAt);
        roomTypes.save(value);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void room(
            UUID eventId, String eventType, String producer, Instant occurredAt,
            UUID roomId, String roomNumber, UUID roomTypeId, String status,
            boolean active, UUID currentStayId, long version) {
        if (processed.existsById(eventId)) return;
        RoomProjection value = rooms.findById(roomId).orElseGet(RoomProjection::new);
        value.synchronize(
                roomId, roomNumber, roomTypeId, status, active,
                currentStayId, version, occurredAt);
        rooms.save(value);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void roomStatus(
            UUID eventId, String eventType, String producer, Instant occurredAt,
            UUID roomId, String roomNumber, UUID roomTypeId, String status,
            UUID currentStayId, long version) {
        if (processed.existsById(eventId)) return;
        RoomProjection value = rooms.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Room status event arrived before room projection"));
        value.synchronize(
                roomId,
                roomNumber == null ? value.getRoomNumber() : roomNumber,
                roomTypeId == null ? value.getRoomTypeId() : roomTypeId,
                status,
                value.isActive(),
                currentStayId,
                version,
                occurredAt);
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    @Transactional
    public void serviceUsage(
            UUID eventId,
            String eventType,
            String producer,
            ServiceUsageFinancialEvent value) {
        if (processed.existsById(eventId)) return;
        Stay stay = stays.findLockedById(value.stayId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Service usage references an unknown stay"));
        if (stay.getStatus() == StayStatus.CLOSED) {
            throw new IllegalStateException(
                    "Financial service usage cannot be applied to a closed stay");
        }
        if (!stay.getCurrency().equals(value.currency())) {
            throw new IllegalArgumentException(
                    "Service usage currency does not match stay currency");
        }
        BigDecimal delta = value.deltaAmount().setScale(2);
        if (delta.signum() != 0) {
            stay.addCharge(delta);
            String description =
                    (value.serviceName() + " x " + value.quantity() + " [" + value.action() + "]")
                            .substring(
                                    0,
                                    Math.min(
                                            300,
                                            (value.serviceName()
                                                    + " x "
                                                    + value.quantity()
                                                    + " ["
                                                    + value.action()
                                                    + "]").length()));
            folio.save(new StayFolioEntry(
                    stay.getId(),
                    value.usageId(),
                    "SERVICE_" + value.action(),
                    description,
                    delta,
                    value.currency(),
                    value.changedBy()));
            events.append(
                    stay,
                    "stay.folio-updated.v1",
                    assignments.findByStayIdAndStatusOrderByCreatedAtAsc(
                            stay.getId(), AssignmentStatus.ACTIVE),
                    null);
        }
        processed.save(new ProcessedEvent(eventId, eventType, producer));
    }

    public record ServiceUsageFinancialEvent(
            UUID usageId,
            UUID stayId,
            UUID reservationId,
            UUID serviceId,
            String serviceCode,
            String serviceName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            BigDecimal deltaAmount,
            String currency,
            Instant occurredAt,
            String status,
            int revision,
            String action,
            UUID changedBy) {}
}
