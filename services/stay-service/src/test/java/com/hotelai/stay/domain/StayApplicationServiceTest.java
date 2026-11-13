package com.hotelai.stay.domain;

import static com.hotelai.stay.domain.SensitiveDocumentCipherTest.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelai.stay.domain.ReservationStayProjection.ReservationSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class StayApplicationServiceTest {
    @Test
    void checkInOccupiesRoomThenOpensStayAndEmitsEvent() {
        ReservationStayProjectionRepository reservations = mock(ReservationStayProjectionRepository.class);
        RoomTypeProjectionRepository roomTypes = mock(RoomTypeProjectionRepository.class);
        RoomProjectionRepository rooms = mock(RoomProjectionRepository.class);
        StayRepository stays = mock(StayRepository.class);
        StayGuestRepository guests = mock(StayGuestRepository.class);
        RoomAssignmentRepository assignments = mock(RoomAssignmentRepository.class);
        StayFolioEntryRepository folio = mock(StayFolioEntryRepository.class);
        StayOperationRepository operations = mock(StayOperationRepository.class);
        InventoryStayClient inventory = mock(InventoryStayClient.class);
        StayEventService events = mock(StayEventService.class);
        AtomicReference<Stay> storedStay = new AtomicReference<>();
        List<StayGuest> storedGuests = new ArrayList<>();
        List<RoomAssignment> storedAssignments = new ArrayList<>();

        UUID roomTypeId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        ReservationStayProjection reservation = new ReservationStayProjection(
                new ReservationSnapshot(
                        UUID.randomUUID(), "RSV-CHECKIN", UUID.randomUUID(),
                        "Nguyen Van A", "guest@example.com", "0900000000",
                        "CONFIRMED", 1, roomTypeId,
                        LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 19),
                        1, 1, 1, "VND", new BigDecimal("1000000"),
                        new BigDecimal("300000"), new BigDecimal("300000"), UUID.randomUUID()),
                Instant.now());
        RoomProjection room = new RoomProjection();
        room.synchronize(roomId, "101", roomTypeId, "AVAILABLE", true, null, 1, Instant.now());
        RoomTypeProjection type = new RoomTypeProjection();
        type.synchronize(roomTypeId, "DLX", "Deluxe", 2, 1, 3, true, 1, Instant.now());

        when(reservations.findById(reservation.getReservationId())).thenReturn(Optional.of(reservation));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(roomTypes.findById(roomTypeId)).thenReturn(Optional.of(type));
        when(stays.findByReservationId(reservation.getReservationId()))
                .thenAnswer(invocation -> Optional.ofNullable(storedStay.get()));
        when(stays.save(any())).thenAnswer(invocation -> {
            Stay value = invocation.getArgument(0);
            storedStay.set(value);
            return value;
        });
        when(stays.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedStay.get()));
        when(stays.findLockedById(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedStay.get()));
        when(guests.save(any())).thenAnswer(invocation -> {
            StayGuest value = invocation.getArgument(0);
            storedGuests.add(value);
            return value;
        });
        when(guests.findByStayIdOrderByPrimaryGuestDescCreatedAtAsc(any()))
                .thenReturn(storedGuests);
        when(assignments.save(any())).thenAnswer(invocation -> {
            RoomAssignment value = invocation.getArgument(0);
            storedAssignments.add(value);
            return value;
        });
        when(assignments.findByStayIdAndStatusOrderByCreatedAtAsc(any(), any()))
                .thenAnswer(invocation -> {
                    AssignmentStatus requested = invocation.getArgument(1);
                    return storedAssignments.stream()
                            .filter(value -> value.getStatus() == requested)
                            .toList();
                });
        when(assignments.findByStayIdOrderByCreatedAtAsc(any())).thenReturn(storedAssignments);
        when(folio.findByStayIdOrderByRecordedAtAsc(any())).thenReturn(List.of());
        when(operations.findByStayIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(inventory.occupy(any(), any(), anyString()))
                .thenReturn(new InventoryStayClient.RoomOccupancy(
                        roomId, "101", roomTypeId, "OCCUPIED", UUID.randomUUID(), 2));

        StayApplicationService service = new StayApplicationService(
                reservations, roomTypes, rooms, stays, guests, assignments, folio, operations,
                new SensitiveDocumentCipher(properties()), inventory, events, properties(),
                Clock.fixed(
                        Instant.parse("2026-07-17T08:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                new ObjectMapper(), noOpTransactions());

        UUID actorId = UUID.randomUUID();
        StayApplicationService.StayDetails result = service.checkIn(
                actorId,
                new StayCommands.CheckIn(
                        reservation.getReservationId(),
                        List.of(roomId),
                        List.of(
                                new StayCommands.Guest(
                                        "Nguyen Van A", LocalDate.of(1990, 1, 1),
                                        "VN", "CCCD", "079203001234", true),
                                new StayCommands.Guest(
                                        "Nguyen Van B", LocalDate.of(2020, 1, 1),
                                        "VN", "PASSPORT", "P1234567", false)),
                        false,
                        false),
                "checkin-test-0001");

        assertThat(result.stay().getStatus()).isEqualTo(StayStatus.OPEN);
        assertThat(result.assignments()).singleElement()
                .extracting(RoomAssignment::getStatus)
                .isEqualTo(AssignmentStatus.ACTIVE);
        verify(inventory).occupy(roomId, result.stay().getId(), "Check-in RSV-CHECKIN");
        verify(events).append(
                result.stay(), "stay.checked-in.v1", result.assignments(), null);
    }

    private static PlatformTransactionManager noOpTransactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }
}
