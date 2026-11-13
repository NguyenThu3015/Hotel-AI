package com.hotelai.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelai.inventory.common.ApiException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomStatusTest {
    @Test
    void stateMachineAllowsOperationalFlow() {
        assertThat(RoomStatus.AVAILABLE.canTransitionTo(RoomStatus.RESERVED)).isTrue();
        assertThat(RoomStatus.RESERVED.canTransitionTo(RoomStatus.OCCUPIED)).isTrue();
        assertThat(RoomStatus.OCCUPIED.canTransitionTo(RoomStatus.WAITING_CLEANING)).isTrue();
        assertThat(RoomStatus.WAITING_CLEANING.canTransitionTo(RoomStatus.AVAILABLE)).isTrue();
        assertThat(RoomStatus.MAINTENANCE.canTransitionTo(RoomStatus.AVAILABLE)).isTrue();
    }

    @Test
    void stateMachineRejectsSkippingRequiredIntermediateState() {
        assertThat(RoomStatus.OCCUPIED.canTransitionTo(RoomStatus.AVAILABLE)).isFalse();
        assertThat(RoomStatus.WAITING_CLEANING.canTransitionTo(RoomStatus.OCCUPIED)).isFalse();
        assertThat(RoomStatus.MAINTENANCE.canTransitionTo(RoomStatus.OCCUPIED)).isFalse();
    }

    @Test
    void roomOccupancyIsIdempotentForTheOwningStayAndRejectsOtherStays() {
        Floor floor = new Floor("F1", "Floor 1", 1);
        RoomType roomType = new RoomType(
                "DLX", "Deluxe", "Deluxe room", 2, 1, 3,
                new BigDecimal("30.00"), "King", true, Set.of());
        Room room = new Room("101", floor, roomType, null);
        UUID owningStay = UUID.randomUUID();
        UUID otherStay = UUID.randomUUID();

        assertThat(room.occupy(owningStay)).isTrue();
        assertThat(room.occupy(owningStay)).isFalse();
        assertThat(room.getCurrentStayId()).isEqualTo(owningStay);
        assertThat(room.getOperationalStatus()).isEqualTo(RoomStatus.OCCUPIED);

        assertThatThrownBy(() -> room.occupy(otherStay))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> room.releaseFromStay(otherStay))
                .isInstanceOf(ApiException.class);

        assertThat(room.releaseFromStay(owningStay)).isTrue();
        assertThat(room.releaseFromStay(owningStay)).isFalse();
        assertThat(room.getCurrentStayId()).isNull();
        assertThat(room.getOperationalStatus()).isEqualTo(RoomStatus.WAITING_CLEANING);
    }
}
