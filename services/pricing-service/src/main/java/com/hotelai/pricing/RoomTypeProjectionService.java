package com.hotelai.pricing;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoomTypeProjectionService {
  private final RoomTypeReferenceRepository roomTypes;

  RoomTypeProjectionService(RoomTypeReferenceRepository roomTypes) {
    this.roomTypes = roomTypes;
  }

  @Transactional
  void synchronize(UUID id, String code, String name, boolean active, long sourceVersion) {
    RoomTypeReference roomType =
        roomTypes
            .findById(id)
            .orElseGet(() -> new RoomTypeReference(id, code, name, active, sourceVersion));
    roomType.synchronize(code, name, active, sourceVersion);
    roomTypes.save(roomType);
  }
}
