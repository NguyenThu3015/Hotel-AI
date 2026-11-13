package com.hotelai.pricing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PricingDtos {
  private PricingDtos() {}

  record BaseRateRequest(
      @NotNull UUID roomTypeId,
      @NotNull @Positive BigDecimal amount,
      @NotNull LocalDate stayFrom,
      @NotNull LocalDate stayTo,
      @NotNull Instant bookingEffectiveFrom,
      Instant bookingEffectiveTo,
      boolean outlierConfirmed) {}

  record BaseRateView(
      UUID id,
      UUID roomTypeId,
      int version,
      String currency,
      BigDecimal amount,
      LocalDate stayFrom,
      LocalDate stayTo,
      Instant bookingEffectiveFrom,
      Instant bookingEffectiveTo,
      boolean outlierConfirmed,
      Instant createdAt) {}

  record RateRuleRequest(
      UUID ruleKey,
      @NotNull UUID roomTypeId,
      @NotBlank @Size(max = 160) String name,
      @NotNull PriceMode priceMode,
      @NotNull @Positive BigDecimal value,
      @Min(0) @Max(10_000) int priority,
      @NotNull LocalDate stayFrom,
      @NotNull LocalDate stayTo,
      Set<DayOfWeek> daysOfWeek,
      @Min(1) Integer minGuests,
      @Min(1) Integer maxGuests,
      @NotNull Instant bookingEffectiveFrom,
      Instant bookingEffectiveTo,
      boolean outlierConfirmed) {}

  record RateRuleView(
      UUID id,
      UUID ruleKey,
      int version,
      UUID roomTypeId,
      String name,
      PriceMode priceMode,
      BigDecimal value,
      int priority,
      LocalDate stayFrom,
      LocalDate stayTo,
      Set<DayOfWeek> daysOfWeek,
      Integer minGuests,
      Integer maxGuests,
      Instant bookingEffectiveFrom,
      Instant bookingEffectiveTo,
      boolean outlierConfirmed) {}

  record PromotionRequest(
      UUID promotionKey,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String code,
      @NotBlank @Size(max = 180) String name,
      @NotNull DiscountType discountType,
      @NotNull @Positive BigDecimal discountValue,
      @Positive BigDecimal maxDiscount,
      @PositiveOrZero BigDecimal minimumSubtotal,
      @Min(1) Integer minimumNights,
      @Min(1) Integer maximumNights,
      Set<UUID> applicableRoomTypeIds,
      @NotNull Instant validFrom,
      @NotNull Instant validTo,
      @Min(1) Integer totalQuota,
      @Min(1) Integer perCustomerQuota,
      @Positive BigDecimal budgetAmount,
      boolean active) {}

  record PromotionView(
      UUID id,
      UUID promotionKey,
      int version,
      String code,
      String name,
      DiscountType discountType,
      BigDecimal discountValue,
      BigDecimal maxDiscount,
      BigDecimal minimumSubtotal,
      Integer minimumNights,
      Integer maximumNights,
      Set<UUID> applicableRoomTypeIds,
      Instant validFrom,
      Instant validTo,
      Integer totalQuota,
      Integer perCustomerQuota,
      BigDecimal budgetAmount,
      int usedCount,
      BigDecimal budgetUsed,
      boolean active,
      long entityVersion) {}

  record PromotionStatusRequest(boolean active) {}

  record PolicyRequest(
      UUID policyKey,
      @NotNull PolicyType policyType,
      @NotNull PolicyScope scopeType,
      UUID scopeId,
      @NotBlank @Size(max = 180) String name,
      @NotBlank @Size(max = 4000) String guestDisplayText,
      @NotNull Map<String, Object> parameters,
      @NotNull Instant effectiveFrom,
      Instant effectiveTo) {}

  record PolicyView(
      UUID id,
      UUID policyKey,
      int version,
      PolicyType policyType,
      PolicyScope scopeType,
      UUID scopeId,
      String name,
      String guestDisplayText,
      Map<String, Object> parameters,
      Instant effectiveFrom,
      Instant effectiveTo) {}

  record PolicyImpactRequest(
      @NotNull PolicyType policyType,
      @NotNull PolicyScope scopeType,
      UUID scopeId,
      @NotNull Instant effectiveFrom,
      Instant effectiveTo) {}

  record PolicyImpactView(
      List<UUID> conflictingPolicyVersionIds,
      List<UUID> currentlyEffectivePolicyVersionIds,
      boolean existingReservationSnapshotsRemainUnchanged,
      List<String> affectedFutureOperations,
      String limitation) {}

  record QuoteRequest(
      @NotNull UUID roomTypeId,
      @NotNull LocalDate checkIn,
      @NotNull LocalDate checkOut,
      @Min(1) @Max(20) int rooms,
      @Min(1) @Max(100) int adults,
      @Min(0) @Max(100) int children,
      @Size(max = 64) String promotionCode,
      UUID customerId) {}

  record AppliedRule(
      UUID ruleVersionId,
      UUID ruleKey,
      int version,
      String name,
      PriceMode mode,
      BigDecimal value,
      int priority,
      BigDecimal amountBefore,
      BigDecimal amountAfter) {}

  record NightlyPrice(
      LocalDate date,
      UUID baseRateVersionId,
      int baseRateVersion,
      BigDecimal baseRate,
      List<AppliedRule> appliedRules,
      BigDecimal finalNightlyRate,
      int rooms,
      BigDecimal lineTotal) {}

  record PromotionDiscount(
      UUID promotionVersionId,
      UUID promotionKey,
      int version,
      String code,
      DiscountType type,
      BigDecimal configuredValue,
      BigDecimal discountAmount) {}

  record PolicySnapshot(
      UUID policyVersionId,
      UUID policyKey,
      int version,
      PolicyType type,
      PolicyScope scope,
      String guestDisplayText,
      Map<String, Object> parameters) {}

  record FeeBreakdown(
      BigDecimal taxPercent,
      BigDecimal taxAmount,
      BigDecimal serviceFeePercent,
      BigDecimal serviceFeeAmount) {}

  record DepositRequirement(
      boolean required, String type, BigDecimal configuredValue, BigDecimal amount) {}

  record QuoteView(
      UUID quoteId,
      Instant quotedAt,
      Instant validUntil,
      UUID roomTypeId,
      LocalDate checkIn,
      LocalDate checkOut,
      int rooms,
      int adults,
      int children,
      int nights,
      String currency,
      List<NightlyPrice> nightlyPrices,
      BigDecimal roomSubtotal,
      PromotionDiscount promotion,
      BigDecimal discountAmount,
      BigDecimal netSubtotal,
      FeeBreakdown fees,
      BigDecimal totalAmount,
      DepositRequirement deposit,
      List<PolicySnapshot> policies) {}

  record PromotionRedemptionRequest(
      @NotNull UUID reservationId, @NotNull UUID quoteId, @NotNull UUID customerId) {}

  record PromotionRedemptionView(
      UUID id,
      UUID promotionVersionId,
      UUID reservationId,
      UUID customerId,
      UUID quoteId,
      BigDecimal discountAmount,
      RedemptionStatus status,
      Instant createdAt,
      Instant releasedAt) {}

  record PromotionSyncRequest(
      @NotNull UUID quoteId,
      @NotNull UUID customerId,
      boolean compensation) {}

  record PromotionSyncView(
      UUID reservationId,
      UUID quoteId,
      boolean applied,
      UUID promotionVersionId,
      BigDecimal discountAmount,
      String status) {}

  record RoomTypeSyncRequest(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Size(max = 200) String name,
      boolean active,
      @PositiveOrZero long sourceVersion) {}
}
