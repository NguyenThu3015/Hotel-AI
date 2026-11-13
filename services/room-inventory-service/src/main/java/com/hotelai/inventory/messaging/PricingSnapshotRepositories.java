package com.hotelai.inventory.messaging;

import com.hotelai.inventory.domain.ProcessedEvent;
import com.hotelai.inventory.domain.RoomType;
import com.hotelai.inventory.domain.RoomTypePriceSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PricingRoomTypeRepository extends JpaRepository<RoomType, UUID> {}
interface PricingSnapshotRepository extends JpaRepository<RoomTypePriceSnapshot, UUID> {}
interface PricingProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
