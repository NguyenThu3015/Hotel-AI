package com.hotelai.inventory.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RoomStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED,
    WAITING_CLEANING,
    MAINTENANCE;

    public boolean canTransitionTo(RoomStatus target) {
        if (target == this) return true;
        return allowedTargets().contains(target);
    }

    private Set<RoomStatus> allowedTargets() {
        return switch (this) {
            case AVAILABLE -> EnumSet.of(RESERVED, OCCUPIED, MAINTENANCE);
            case RESERVED -> EnumSet.of(AVAILABLE, OCCUPIED, MAINTENANCE);
            case OCCUPIED -> EnumSet.of(WAITING_CLEANING, MAINTENANCE);
            case WAITING_CLEANING -> EnumSet.of(AVAILABLE, MAINTENANCE);
            case MAINTENANCE -> EnumSet.of(AVAILABLE, WAITING_CLEANING);
        };
    }
}
