package com.hotelai.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InventoryReadModelPublisher {
  private final BaseRateVersionRepository baseRates;
  private final PolicyVersionRepository policies;
  private final RoomTypeReferenceRepository roomTypes;
  private final OutboxService outbox;

  InventoryReadModelPublisher(
      BaseRateVersionRepository baseRates,
      PolicyVersionRepository policies,
      RoomTypeReferenceRepository roomTypes,
      OutboxService outbox) {
    this.baseRates = baseRates;
    this.policies = policies;
    this.roomTypes = roomTypes;
    this.outbox = outbox;
  }

  void publishIfEffective(BaseRateVersion rate) {
    Instant now = Instant.now();
    if (rate.isInventorySnapshotPending()
        && !now.isBefore(rate.getBookingEffectiveFrom())
        && (rate.getBookingEffectiveTo() == null || now.isBefore(rate.getBookingEffectiveTo()))) {
      publishPrice(rate);
    }
  }

  void publishIfEffective(PolicyVersion policy) {
    Instant now = Instant.now();
    if (policy.isInventorySnapshotPending() && policy.activeAt(now)) {
      publishPolicy(policy);
    }
  }

  @Scheduled(fixedDelayString = "${hotel.pricing.inventory-snapshot-delay-ms:1000}")
  @Transactional
  public void publishScheduledActivations() {
    Instant now = Instant.now();
    baseRates.findInventorySnapshotsDue(now, PageRequest.of(0, 50)).forEach(this::publishPrice);
    policies.findInventorySnapshotsDue(now, PageRequest.of(0, 50)).forEach(this::publishPolicy);
  }

  private void publishPrice(BaseRateVersion rate) {
    outbox.append(
        "BaseRate",
        rate.getId(),
        "pricing.room-type-price-published.v1",
        map(
            "roomTypeId",
            rate.getRoomTypeId(),
            "nightlyPrice",
            rate.getAmount(),
            "currency",
            rate.getCurrency(),
            "taxRate",
            BigDecimal.ZERO,
            "serviceFee",
            BigDecimal.ZERO,
            "pricingVersion",
            rate.getVersionNo(),
            "effectiveAt",
            rate.getBookingEffectiveFrom()));
    rate.markInventorySnapshotPublished();
  }

  private void publishPolicy(PolicyVersion policy) {
    List<UUID> affectedRoomTypes =
        policy.getScopeType() == PolicyScope.ROOM_TYPE
            ? List.of(policy.getScopeId())
            : roomTypes.findByActiveTrueOrderByCodeAsc().stream()
                .map(RoomTypeReference::getId)
                .toList();
    affectedRoomTypes.forEach(
        roomTypeId ->
            outbox.append(
                "Policy",
                policy.getId(),
                "pricing.room-type-policy-published.v1",
                map(
                    "roomTypeId",
                    roomTypeId,
                    "summary",
                    policy.getGuestDisplayText(),
                    "policyVersion",
                    policy.getVersionNo())));
    policy.markInventorySnapshotPublished();
  }

  private static Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }
}
