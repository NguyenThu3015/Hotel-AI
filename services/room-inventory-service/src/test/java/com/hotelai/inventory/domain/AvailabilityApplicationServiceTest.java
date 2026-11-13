package com.hotelai.inventory.domain;

import static com.hotelai.inventory.api.InventoryDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hotelai.inventory.common.ApiException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityApplicationServiceTest {
    @Mock RoomTypeRepository roomTypes;
    @Mock RoomRepository rooms;
    @Mock MaintenanceBlockRepository maintenance;
    @Mock InventoryHoldRepository holds;
    @Mock RoomTypePriceSnapshotRepository prices;
    @Mock OutboxEventService outbox;
    @Mock EntityManager entityManager;

    private AvailabilityApplicationService service;
    private RoomType type;

    @BeforeEach
    void setUp() {
        service = new AvailabilityApplicationService(
                roomTypes, rooms, maintenance, holds, prices, outbox, entityManager, 15, 90);
        type = new RoomType(
                "DELUXE", "Deluxe", "Phòng Deluxe", 2, 1, 3,
                BigDecimal.valueOf(28), "1 giường đôi", true, Set.of());
        type.updatePolicySummary("Không hoàn tiền", 1);
    }

    @Test
    void searchSubtractsMaintenanceAndLiveHoldsAndBuildsPriceEstimate() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        RoomTypePriceSnapshot price = new RoomTypePriceSnapshot(
                type.getId(), BigDecimal.valueOf(1_000_000), "VND", new BigDecimal("0.10"),
                BigDecimal.valueOf(100_000), 3, Instant.now());
        when(roomTypes.findPublishedDetailed()).thenReturn(List.of(type));
        when(prices.findAllById(any())).thenReturn(List.of(price));
        when(rooms.countActiveByRoomType(type.getId())).thenReturn(5L);
        when(maintenance.countUnavailableRooms(type.getId(), checkIn, checkOut)).thenReturn(1L);
        when(holds.sumCommittedQuantity(eq(type.getId()), eq(checkIn), eq(checkOut), any(Instant.class)))
                .thenReturn(2L);

        AvailabilityResponse response = service.search(new SearchCriteria(
                checkIn, checkOut, 2, 0, 1, Set.of(), Set.of(), null, null,
                AvailabilitySort.PRICE_ASC));

        assertThat(response.content()).hasSize(1);
        AvailabilityItem item = response.content().getFirst();
        assertThat(item.availableRooms()).isEqualTo(2);
        assertThat(item.price().subtotal()).isEqualByComparingTo("2000000.00");
        assertThat(item.price().tax()).isEqualByComparingTo("210000.00");
        assertThat(item.price().total()).isEqualByComparingTo("2310000.00");
    }

    @Test
    void createHoldLocksRoomTypeAndRejectsOverselling() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(1);
        when(holds.findByIdempotencyKey("reservation-123")).thenReturn(Optional.empty());
        when(roomTypes.findByIdForUpdate(type.getId())).thenReturn(Optional.of(type));
        when(rooms.countActiveByRoomType(type.getId())).thenReturn(2L);
        when(maintenance.countUnavailableRooms(type.getId(), checkIn, checkOut)).thenReturn(0L);
        when(holds.sumCommittedQuantity(eq(type.getId()), eq(checkIn), eq(checkOut), any(Instant.class)))
                .thenReturn(2L);

        CreateHoldRequest request = new CreateHoldRequest(
                type.getId(), checkIn, checkOut, 1, "BK-001", 15);

        assertThatThrownBy(() -> service.createHold("reservation-123", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chỉ còn 0 phòng");
        verify(roomTypes).findByIdForUpdate(type.getId());
        verify(holds, never()).save(any());
    }

    @Test
    void createHoldPersistsOutboxInSameApplicationTransaction() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(1);
        when(holds.findByIdempotencyKey("reservation-456")).thenReturn(Optional.empty());
        when(roomTypes.findByIdForUpdate(type.getId())).thenReturn(Optional.of(type));
        when(rooms.countActiveByRoomType(type.getId())).thenReturn(2L);
        when(maintenance.countUnavailableRooms(type.getId(), checkIn, checkOut)).thenReturn(0L);
        when(holds.sumCommittedQuantity(eq(type.getId()), eq(checkIn), eq(checkOut), any(Instant.class)))
                .thenReturn(0L);
        when(holds.save(any(InventoryHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HoldView result = service.createHold(
                "reservation-456",
                new CreateHoldRequest(type.getId(), checkIn, checkOut, 1, "BK-002", null));

        assertThat(result.status()).isEqualTo("HELD");
        assertThat(result.expiresAt()).isAfter(Instant.now().plusSeconds(13 * 60));
        verify(outbox).emit(
                eq("InventoryHold"), eq(result.id()), eq("inventory.hold.created.v1"), anyMap());
        verify(holds).lockIdempotencyKey("reservation-456");
    }

    @Test
    void replayReturnsExistingHoldAfterDatabaseIdempotencyLock() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(1);
        CreateHoldRequest request = new CreateHoldRequest(
                type.getId(), checkIn, checkOut, 1, "BK-003", 15);
        InventoryHold existing = new InventoryHold(
                type, "BK-003", checkIn, checkOut, 1, Instant.now().plusSeconds(900),
                "reservation-789", requestHashForTest(request));
        when(holds.findByIdempotencyKey("reservation-789")).thenReturn(Optional.of(existing));

        HoldView replay = service.createHold("reservation-789", request);

        assertThat(replay.id()).isEqualTo(existing.getId());
        verify(holds).lockIdempotencyKey("reservation-789");
        verifyNoInteractions(rooms, maintenance, prices, outbox, entityManager);
    }

    @Test
    void replacingAConfirmedHoldReleasesAndConfirmsInOneTransaction() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        java.util.UUID reservationId = java.util.UUID.randomUUID();
        InventoryHold previous = new InventoryHold(
                type, reservationId.toString(), checkIn, checkIn.plusDays(1), 1,
                Instant.now().plusSeconds(900), "previous-hold", "old-hash");
        previous.confirm(reservationId);
        InventoryHold replacement = new InventoryHold(
                type, reservationId.toString(), checkIn, checkIn.plusDays(2), 1,
                Instant.now().plusSeconds(900), "replacement-hold", "new-hash");
        when(holds.findByIdForUpdate(previous.getId())).thenReturn(Optional.of(previous));
        when(holds.findByIdForUpdate(replacement.getId())).thenReturn(Optional.of(replacement));

        HoldView result = service.replaceConfirmedHold(
                previous.getId(), replacement.getId(), reservationId);

        assertThat(previous.getStatus()).isEqualTo("RELEASED");
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.reservationId()).isEqualTo(reservationId);
        verify(entityManager, times(2)).flush();
        verify(outbox).emit(
                eq("InventoryHold"), eq(previous.getId()),
                eq("inventory.hold.released.v1"), anyMap());
        verify(outbox).emit(
                eq("InventoryHold"), eq(replacement.getId()),
                eq("inventory.hold.confirmed.v1"), anyMap());
    }

    @Test
    void replacingWithAStalePreviousIdFindsTheCurrentConfirmedHold() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        java.util.UUID reservationId = java.util.UUID.randomUUID();
        InventoryHold stalePrevious = new InventoryHold(
                type, reservationId.toString(), checkIn, checkIn.plusDays(1), 1,
                Instant.now().plusSeconds(900), "stale-previous-hold", "stale-hash");
        stalePrevious.confirm(reservationId);
        stalePrevious.release();
        InventoryHold current = new InventoryHold(
                type, reservationId.toString(), checkIn, checkIn.plusDays(2), 1,
                Instant.now().plusSeconds(900), "current-hold", "current-hash");
        current.confirm(reservationId);
        InventoryHold replacement = new InventoryHold(
                type, reservationId.toString(), checkIn, checkIn.plusDays(3), 1,
                Instant.now().plusSeconds(900), "next-hold", "next-hash");
        when(holds.findByIdForUpdate(stalePrevious.getId()))
                .thenReturn(Optional.of(stalePrevious));
        when(holds.findByIdForUpdate(replacement.getId()))
                .thenReturn(Optional.of(replacement));
        when(holds.findConfirmedByReservationIdForUpdate(reservationId))
                .thenReturn(Optional.of(current));

        HoldView result = service.replaceConfirmedHold(
                stalePrevious.getId(), replacement.getId(), reservationId);

        assertThat(stalePrevious.getStatus()).isEqualTo("RELEASED");
        assertThat(current.getStatus()).isEqualTo("RELEASED");
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.reservationId()).isEqualTo(reservationId);
        verify(holds).findConfirmedByReservationIdForUpdate(reservationId);
        verify(outbox).emit(
                eq("InventoryHold"), eq(current.getId()),
                eq("inventory.hold.released.v1"), anyMap());
        verify(outbox).emit(
                eq("InventoryHold"), eq(replacement.getId()),
                eq("inventory.hold.confirmed.v1"), anyMap());
    }

    private static String requestHashForTest(CreateHoldRequest request) {
        try {
            String canonical = String.join("|", request.roomTypeId().toString(),
                    request.checkIn().toString(), request.checkOut().toString(),
                    String.valueOf(request.quantity()), request.externalReference(),
                    request.ttlMinutes().toString());
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
