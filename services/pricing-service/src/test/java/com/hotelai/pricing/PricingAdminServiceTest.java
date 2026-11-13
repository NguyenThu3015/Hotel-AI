package com.hotelai.pricing;

import static com.hotelai.pricing.PricingDtos.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PricingAdminServiceTest {
  private final RoomTypeReferenceRepository roomTypes = mock(RoomTypeReferenceRepository.class);
  private final BaseRateVersionRepository baseRates = mock(BaseRateVersionRepository.class);
  private final RateRuleVersionRepository rules = mock(RateRuleVersionRepository.class);
  private final PromotionVersionRepository promotions = mock(PromotionVersionRepository.class);
  private final PolicyVersionRepository policies = mock(PolicyVersionRepository.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final InventoryReadModelPublisher inventoryReadModels =
      mock(InventoryReadModelPublisher.class);
  private final ObjectMapper objectMapper = mock(ObjectMapper.class);
  private final UUID roomTypeId = UUID.randomUUID();
  private PricingAdminService service;

  @BeforeEach
  void setUp() {
    service =
        new PricingAdminService(
            roomTypes,
            baseRates,
            rules,
            promotions,
            policies,
            outbox,
            inventoryReadModels,
            objectMapper,
            "VND",
            new BigDecimal("100000"),
            new BigDecimal("100000000"));
    when(roomTypes.findById(roomTypeId))
        .thenReturn(Optional.of(new RoomTypeReference(roomTypeId, "DLX", "Deluxe", true, 1)));
  }

  @Test
  void rateOutsideConfiguredRangeRequiresExplicitConfirmation() {
    LocalDate start = LocalDate.now().plusDays(1);
    BaseRateRequest request =
        new BaseRateRequest(
            roomTypeId,
            new BigDecimal("50000"),
            start,
            start.plusDays(2),
            Instant.now(),
            null,
            false);

    assertThatThrownBy(() -> service.createBaseRate(request, UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("outlierConfirmed=true");
    verify(baseRates, never()).save(any());
  }

  @Test
  void overlappingDifferentRulesCannotSharePriority() {
    LocalDate start = LocalDate.now().plusDays(1);
    Instant effective = Instant.now().minusSeconds(60);
    RateRuleVersion existing =
        new RateRuleVersion(
            UUID.randomUUID(),
            1,
            roomTypeId,
            "Weekend",
            PriceMode.MULTIPLIER,
            new BigDecimal("1.2"),
            100,
            start,
            start.plusDays(10),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            null,
            null,
            effective,
            null,
            false,
            UUID.randomUUID());
    when(rules.findByRoomTypeIdOrderByPriorityAscVersionNoDesc(roomTypeId))
        .thenReturn(List.of(existing));

    RateRuleRequest request =
        new RateRuleRequest(
            null,
            roomTypeId,
            "Holiday weekend",
            PriceMode.MULTIPLIER,
            new BigDecimal("1.4"),
            100,
            start.plusDays(2),
            start.plusDays(6),
            Set.of(DayOfWeek.SUNDAY),
            null,
            null,
            effective,
            null,
            false);

    assertThatThrownBy(() -> service.createRateRule(request, UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("same priority");
    verify(rules, never()).save(any());
  }

  @Test
  void invalidDepositPolicyIsRejectedBeforePersistence() {
    PolicyRequest request =
        new PolicyRequest(
            null,
            PolicyType.DEPOSIT,
            PolicyScope.GLOBAL,
            null,
            "Deposit",
            "Deposit policy",
            java.util.Map.of("required", true, "depositType", "PERCENT", "depositValue", 120),
            Instant.now(),
            null);

    assertThatThrownBy(() -> service.createPolicy(request, UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot exceed 100");
    verify(policies, never()).save(any());
  }
}
