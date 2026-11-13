package com.hotelai.pricing;

import static com.hotelai.pricing.PricingDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

class QuoteServiceTest {
  private final RoomTypeReferenceRepository roomTypes = mock(RoomTypeReferenceRepository.class);
  private final BaseRateVersionRepository baseRates = mock(BaseRateVersionRepository.class);
  private final RateRuleVersionRepository rules = mock(RateRuleVersionRepository.class);
  private final PromotionVersionRepository promotions = mock(PromotionVersionRepository.class);
  private final PromotionRedemptionRepository redemptions =
      mock(PromotionRedemptionRepository.class);
  private final PolicyVersionRepository policies = mock(PolicyVersionRepository.class);
  private final PriceQuoteRepository quotes = mock(PriceQuoteRepository.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final IdempotencyService idempotency = mock(IdempotencyService.class);
  private final ObjectMapper objectMapper = mock(ObjectMapper.class);
  private final UUID roomTypeId = UUID.randomUUID();
  private QuoteService service;

  @BeforeEach
  void setUp() {
    service =
        new QuoteService(
            roomTypes,
            baseRates,
            rules,
            promotions,
            redemptions,
            policies,
            quotes,
            outbox,
            idempotency,
            objectMapper,
            "VND",
            15);
    when(roomTypes.findById(roomTypeId))
        .thenReturn(Optional.of(new RoomTypeReference(roomTypeId, "DLX", "Deluxe", true, 1)));
    when(policies.findEffective(any(), eq(roomTypeId), any())).thenReturn(List.of());
  }

  @Test
  void quoteAppliesVersionedRulesInPriorityOrderForEveryNightAndRoom() {
    LocalDate checkIn = LocalDate.now().plusDays(2);
    LocalDate checkOut = checkIn.plusDays(2);
    Instant effectiveFrom = Instant.now().minusSeconds(60);
    BaseRateVersion base =
        new BaseRateVersion(
            roomTypeId,
            1,
            "VND",
            new BigDecimal("1000000"),
            checkIn.minusDays(1),
            checkOut.plusDays(1),
            effectiveFrom,
            null,
            false,
            UUID.randomUUID());
    RateRuleVersion multiplier =
        new RateRuleVersion(
            UUID.randomUUID(),
            1,
            roomTypeId,
            "Peak season",
            PriceMode.MULTIPLIER,
            new BigDecimal("1.20"),
            10,
            checkIn,
            checkOut,
            Set.of(DayOfWeek.values()),
            null,
            null,
            effectiveFrom,
            null,
            false,
            UUID.randomUUID());
    RateRuleVersion surcharge =
        new RateRuleVersion(
            UUID.randomUUID(),
            1,
            roomTypeId,
            "Guest surcharge",
            PriceMode.ADDITIVE_SURCHARGE,
            new BigDecimal("100000"),
            20,
            checkIn,
            checkOut,
            Set.of(DayOfWeek.values()),
            3,
            null,
            effectiveFrom,
            null,
            false,
            UUID.randomUUID());
    when(baseRates.findApplicable(eq(roomTypeId), any(), any())).thenReturn(List.of(base));
    when(rules.findCandidates(eq(roomTypeId), any(), any()))
        .thenReturn(List.of(multiplier, surcharge));

    QuoteView result =
        service.simulate(new QuoteRequest(roomTypeId, checkIn, checkOut, 2, 3, 0, null, null));

    assertThat(result.nights()).isEqualTo(2);
    assertThat(result.nightlyPrices()).hasSize(2);
    assertThat(result.nightlyPrices().getFirst().finalNightlyRate())
        .isEqualByComparingTo("1300000.00");
    assertThat(result.nightlyPrices().getFirst().lineTotal()).isEqualByComparingTo("2600000.00");
    assertThat(result.roomSubtotal()).isEqualByComparingTo("5200000.00");
    assertThat(result.totalAmount()).isEqualByComparingTo("5200000.00");
    assertThat(result.nightlyPrices().getFirst().appliedRules())
        .extracting(AppliedRule::priority)
        .containsExactly(10, 20);
  }

  @Test
  void promotionWithPerCustomerQuotaRequiresCustomerIdentityAtQuoteTime() {
    LocalDate checkIn = LocalDate.now().plusDays(2);
    BaseRateVersion base =
        new BaseRateVersion(
            roomTypeId,
            1,
            "VND",
            new BigDecimal("1000000"),
            checkIn,
            checkIn.plusDays(2),
            Instant.now().minusSeconds(60),
            null,
            false,
            UUID.randomUUID());
    PromotionVersion promotion =
        new PromotionVersion(
            UUID.randomUUID(),
            1,
            "LOYAL10",
            "Loyal customer",
            DiscountType.PERCENT,
            BigDecimal.TEN,
            null,
            null,
            null,
            null,
            Set.of(),
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600),
            100,
            1,
            null,
            true,
            UUID.randomUUID());
    when(baseRates.findApplicable(eq(roomTypeId), any(), any())).thenReturn(List.of(base));
    when(rules.findCandidates(eq(roomTypeId), any(), any())).thenReturn(List.of());
    when(promotions.findEffectiveByCode(eq("LOYAL10"), any())).thenReturn(List.of(promotion));

    assertThatThrownBy(
            () ->
                service.simulate(
                    new QuoteRequest(
                        roomTypeId, checkIn, checkIn.plusDays(1), 1, 2, 0, "LOYAL10", null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("conditions are not met");
  }

  @Test
  void quoteFailsClearlyWhenAnyNightHasNoBaseRate() {
    LocalDate checkIn = LocalDate.now().plusDays(2);
    when(baseRates.findApplicable(eq(roomTypeId), any(), any())).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.simulate(
                    new QuoteRequest(
                        roomTypeId, checkIn, checkIn.plusDays(1), 1, 2, 0, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("No base rate");
  }

  @Test
  void synchronizePromotionConsumesQuotaOnlyWhenReservationAcceptsQuote() {
    UUID customerId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    UUID promotionId = UUID.randomUUID();
    PromotionVersion promotion = mock(PromotionVersion.class);
    when(promotion.getId()).thenReturn(promotionId);
    when(promotion.getPerCustomerQuota()).thenReturn(1);
    PriceQuote quote =
        new PriceQuote(
            quoteId,
            "quote-key",
            "request-hash",
            roomTypeId,
            customerId,
            promotionId,
            new BigDecimal("100000"),
            "{}",
            Instant.now(),
            Instant.now().plusSeconds(300));
    when(quotes.findById(quoteId)).thenReturn(Optional.of(quote));
    when(redemptions.findLockedByReservationId(reservationId)).thenReturn(Optional.empty());
    when(promotions.findLockedById(promotionId)).thenReturn(Optional.of(promotion));
    when(redemptions.countByPromotionVersionIdAndCustomerIdAndStatus(
            promotionId, customerId, RedemptionStatus.APPLIED))
        .thenReturn(0L);
    when(redemptions.save(any(PromotionRedemption.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PromotionSyncView result =
        service.synchronizePromotion(
            reservationId, new PromotionSyncRequest(quoteId, customerId, false));

    assertThat(result.applied()).isTrue();
    assertThat(result.reservationId()).isEqualTo(reservationId);
    assertThat(result.quoteId()).isEqualTo(quoteId);
    assertThat(result.discountAmount()).isEqualByComparingTo("100000.00");
    verify(promotion).redeem(new BigDecimal("100000"));
    verify(outbox)
        .append(
            eq("PromotionRedemption"),
            any(),
            eq("pricing.promotion.redeemed.v1"),
            any());
  }

  @Test
  void synchronizePromotionReleasesPreviousQuotaWhenReplacementHasNoDiscount() {
    UUID customerId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    UUID oldQuoteId = UUID.randomUUID();
    UUID replacementQuoteId = UUID.randomUUID();
    PromotionVersion promotion =
        new PromotionVersion(
            UUID.randomUUID(),
            1,
            "OLD10",
            "Old discount",
            DiscountType.PERCENT,
            BigDecimal.TEN,
            null,
            null,
            null,
            null,
            Set.of(),
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600),
            10,
            1,
            null,
            true,
            UUID.randomUUID());
    promotion.redeem(new BigDecimal("100000"));
    PromotionRedemption redemption =
        new PromotionRedemption(
            promotion.getId(),
            reservationId,
            customerId,
            oldQuoteId,
            new BigDecimal("100000"));
    PriceQuote replacement =
        new PriceQuote(
            replacementQuoteId,
            "replacement-key",
            "replacement-hash",
            roomTypeId,
            customerId,
            null,
            BigDecimal.ZERO,
            "{}",
            Instant.now(),
            Instant.now().plusSeconds(300));
    when(quotes.findById(replacementQuoteId)).thenReturn(Optional.of(replacement));
    when(redemptions.findLockedByReservationId(reservationId))
        .thenReturn(Optional.of(redemption));
    when(promotions.findLockedById(promotion.getId())).thenReturn(Optional.of(promotion));

    PromotionSyncView result =
        service.synchronizePromotion(
            reservationId, new PromotionSyncRequest(replacementQuoteId, customerId, false));

    assertThat(result.applied()).isFalse();
    assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RELEASED);
    verify(outbox)
        .append(
            eq("PromotionRedemption"),
            any(),
            eq("pricing.promotion.released.v1"),
            any());
  }
}
