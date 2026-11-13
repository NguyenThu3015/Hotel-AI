package com.hotelai.inventory.domain;

import static com.hotelai.inventory.api.InventoryDtos.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.hotelai.inventory.common.ApiException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogApplicationServiceTest {
    @Mock FloorRepository floors;
    @Mock AmenityRepository amenities;
    @Mock RoomTypeRepository roomTypes;
    @Mock RoomImageRepository images;
    @Mock RoomRepository rooms;
    @Mock RoomStatusHistoryRepository histories;
    @Mock MaintenanceBlockRepository maintenance;
    @Mock InventoryHoldRepository holds;
    @Mock OutboxEventService outbox;
    @Mock EntityManager entityManager;

    private CatalogApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CatalogApplicationService(
                floors, amenities, roomTypes, images, rooms, histories, maintenance, holds, outbox,
                entityManager);
    }

    @Test
    void cannotDeactivateRoomTypeWithFutureCommitment() {
        RoomType type = roomType();
        when(roomTypes.findDetailedById(type.getId())).thenReturn(Optional.of(type));
        when(holds.hasFutureCommitments(eq(type.getId()), any(LocalDate.class), any(Instant.class)))
                .thenReturn(true);

        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(
                "DELUXE", "Deluxe", "Mô tả phòng", 2, 1, 3, BigDecimal.valueOf(28),
                "1 giường đôi", false, false, Set.of(), 0);

        assertThatThrownBy(() -> service.updateRoomType(type.getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đặt phòng tương lai");
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsStaleRoomStatusCommandBeforeWritingHistory() {
        Floor floor = new Floor("F1", "Tầng 1", 1);
        Room room = new Room("101", floor, roomType(), null);
        when(rooms.findForStatusChange(room.getId())).thenReturn(Optional.of(room));

        RoomStatusChangeRequest request = new RoomStatusChangeRequest(
                RoomStatus.RESERVED, "Giữ cho khách", 2);

        assertThatThrownBy(() -> service.changeStatus(room.getId(), request, "test", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("phiên bản hiện tại");
        verifyNoInteractions(histories, maintenance, outbox);
    }

    @Test
    void occupiedRoomCannotJumpDirectlyToAvailable() {
        Floor floor = new Floor("F1", "Tầng 1", 1);
        Room room = new Room("101", floor, roomType(), null);
        room.transitionTo(RoomStatus.OCCUPIED);
        when(rooms.findForStatusChange(room.getId())).thenReturn(Optional.of(room));

        RoomStatusChangeRequest request = new RoomStatusChangeRequest(
                RoomStatus.AVAILABLE, "Bỏ qua dọn phòng", 0);

        assertThatThrownBy(() -> service.changeStatus(room.getId(), request, "test", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không thể chuyển trạng thái");
        verifyNoInteractions(histories, maintenance, outbox);
    }

    @Test
    void maintenanceTransitionWritesImmutableHistoryAndEvent() {
        Floor floor = new Floor("F1", "Tầng 1", 1);
        Room room = new Room("101", floor, roomType(), null);
        when(rooms.findForStatusChange(room.getId())).thenReturn(Optional.of(room));
        when(maintenance.save(any(MaintenanceBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomView result = service.changeStatus(
                room.getId(), new RoomStatusChangeRequest(RoomStatus.MAINTENANCE, "Sửa điều hòa", 0),
                "admin-api", UUID.randomUUID());

        verify(histories).save(any(RoomStatusHistory.class));
        verify(maintenance).save(any(MaintenanceBlock.class));
        verify(outbox).emit(
                eq("Room"), eq(room.getId()), eq("inventory.room.status-changed.v1"), anyMap());
        verify(entityManager).flush();
        org.assertj.core.api.Assertions.assertThat(result.status()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    private static RoomType roomType() {
        return new RoomType(
                "DELUXE", "Deluxe", "Mô tả phòng", 2, 1, 3,
                BigDecimal.valueOf(28), "1 giường đôi", true, Set.of());
    }
}
