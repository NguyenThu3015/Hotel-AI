package com.hotelai.reservation.domain;

import java.time.Instant;
import java.util.UUID;

public record InventoryHold(UUID holdId, Instant expiresAt, long version) {}
