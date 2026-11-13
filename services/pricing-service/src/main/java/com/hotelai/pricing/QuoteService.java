package com.hotelai.pricing;

import static com.hotelai.pricing.PricingDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class QuoteService {
  private final RoomTypeReferenceRepository roomTypes;
  private final BaseRateVersionRepository baseRates;
  private final RateRuleVersionRepository rules;
  private final PromotionVersionRepository promotions;
  private final PromotionRedemptionRepository redemptions;
  private final PolicyVersionRepository policies;
  private final PriceQuoteRepository quotes;
  private final OutboxService outbox;
  private final IdempotencyService idempotency;
  private final ObjectMapper objectMapper;
  private final String currency;
  private final Duration quoteTtl;

  QuoteService(
      RoomTypeReferenceRepository roomTypes,
      BaseRateVersionRepository baseRates,
      RateRuleVersionRepository rules,
      PromotionVersionRepository promotions,
      PromotionRedemptionRepository redemptions,
      PolicyVersionRepository policies,
      PriceQuoteRepository quotes,
      OutboxService outbox,
      IdempotencyService idempotency,
      ObjectMapper objectMapper,
      @Value("${hotel.pricing.currency}") String currency,
      @Value("${hotel.pricing.quote-ttl-minutes}") long quoteTtlMinutes) {
    this.roomTypes = roomTypes;
    this.baseRates = baseRates;
    this.rules = rules;
    this.promotions = promotions;
    this.redemptions = redemptions;
    this.policies = policies;
    this.quotes = quotes;
    this.outbox = outbox;
    this.idempotency = idempotency;
    this.objectMapper = objectMapper;
    this.currency = currency;
    this.quoteTtl = Duration.ofMinutes(quoteTtlMinutes);
  }

  @Transactional
  QuoteView createQuote(String idempotencyKey, QuoteRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_idempotency_key",
          "Idempotency-Key must contain between 1 and 160 characters");
    }
    String requestHash = idempotency.requestHash(request);
    quotes.lockIdempotencyKey(idempotencyKey);
    PriceQuote existing = quotes.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (existing != null) {
      if (!existing.getRequestHash().equals(requestHash)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "idempotency_key_reused",
            "Idempotency-Key was already used with a different quote request");
      }
      return deserialize(existing.getResponseSnapshot(), QuoteView.class);
    }
    validateRequest(request);
    Instant now = Instant.now();
    UUID quoteId = UUID.randomUUID();
    ComputedQuote computed = compute(request, now, quoteId);
    String snapshot = serialize(computed.view());
    quotes.save(
        new PriceQuote(
            quoteId,
            idempotencyKey,
            requestHash,
            request.roomTypeId(),
            request.customerId(),
            computed.promotionVersionId(),
            computed.view().discountAmount(),
            snapshot,
            now,
            computed.view().validUntil()));
    return computed.view();
  }

  @Transactional(readOnly = true)
  QuoteView simulate(QuoteRequest request) {
    validateRequest(request);
    return compute(request, Instant.now(), null).view();
  }

  @Transactional
  PromotionRedemptionView redeem(PromotionRedemptionRequest request) {
    PromotionRedemption existing =
        redemptions.findByReservationId(request.reservationId()).orElse(null);
    if (existing != null) {
      if (!existing.getQuoteId().equals(request.quoteId())
          || !existing.getCustomerId().equals(request.customerId())) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "reservation_promotion_already_recorded",
            "Reservation already has a different promotion redemption");
      }
      return redemptionView(existing);
    }
    PriceQuote quote =
        quotes
            .findById(request.quoteId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "quote_not_found", "Price quote was not found"));
    Instant now = Instant.now();
    if (!now.isBefore(quote.getExpiresAt())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "quote_expired",
          "Price quote expired; obtain a new quote before creating the reservation");
    }
    if (quote.getPromotionVersionId() == null || quote.getDiscountAmount().signum() <= 0) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "quote_has_no_promotion",
          "The quote has no promotion to redeem");
    }
    if (quote.getCustomerId() != null && !quote.getCustomerId().equals(request.customerId())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "quote_customer_mismatch",
          "Price quote belongs to another customer");
    }
    PromotionVersion promotion =
        promotions
            .findLockedById(quote.getPromotionVersionId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "promotion_version_not_found",
                        "Promotion version referenced by quote was not found"));
    long customerUseCount =
        redemptions.countByPromotionVersionIdAndCustomerIdAndStatus(
            promotion.getId(), request.customerId(), RedemptionStatus.APPLIED);
    if (promotion.getPerCustomerQuota() != null
        && customerUseCount >= promotion.getPerCustomerQuota()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "customer_promotion_quota_exhausted",
          "Customer promotion quota has been exhausted");
    }
    promotion.redeem(quote.getDiscountAmount());
    PromotionRedemption redemption =
        redemptions.save(
            new PromotionRedemption(
                promotion.getId(),
                request.reservationId(),
                request.customerId(),
                quote.getId(),
                quote.getDiscountAmount()));
    outbox.append(
        "PromotionRedemption",
        redemption.getId(),
        "pricing.promotion.redeemed.v1",
        eventPayload(redemption));
    return redemptionView(redemption);
  }

  @Transactional
  PromotionRedemptionView release(UUID reservationId) {
    PromotionRedemption redemption =
        redemptions
            .findByReservationId(reservationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "promotion_redemption_not_found",
                        "Promotion redemption was not found"));
    if (redemption.getStatus() == RedemptionStatus.RELEASED) {
      return redemptionView(redemption);
    }
    PromotionVersion promotion =
        promotions
            .findLockedById(redemption.getPromotionVersionId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "promotion_version_not_found",
                        "Promotion version referenced by redemption was not found"));
    promotion.release(redemption.getDiscountAmount());
    redemption.release();
    outbox.append(
        "PromotionRedemption",
        redemption.getId(),
        "pricing.promotion.released.v1",
        eventPayload(redemption));
    return redemptionView(redemption);
  }

  @Transactional
  PromotionSyncView synchronizePromotion(
      UUID reservationId, PromotionSyncRequest request) {
    PriceQuote quote =
        quotes
            .findById(request.quoteId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "quote_not_found", "Price quote was not found"));
    if (!request.compensation() && !Instant.now().isBefore(quote.getExpiresAt())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "quote_expired",
          "Price quote expired; obtain a new quote before changing the reservation");
    }
    if (quote.getCustomerId() != null && !quote.getCustomerId().equals(request.customerId())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "quote_customer_mismatch",
          "Price quote belongs to another customer");
    }

    PromotionRedemption redemption =
        redemptions.findLockedByReservationId(reservationId).orElse(null);
    if (redemption != null && !redemption.getCustomerId().equals(request.customerId())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "reservation_customer_mismatch",
          "Promotion redemption belongs to another customer");
    }
    UUID targetPromotionId = quote.getPromotionVersionId();
    if (targetPromotionId == null || quote.getDiscountAmount().signum() <= 0) {
      releaseExisting(redemption);
      return new PromotionSyncView(
          reservationId, quote.getId(), false, null, BigDecimal.ZERO, "NOT_APPLIED");
    }
    if (redemption != null
        && redemption.getStatus() == RedemptionStatus.APPLIED
        && redemption.getQuoteId().equals(quote.getId())) {
      return promotionSyncView(redemption);
    }

    PromotionVersion target =
        promotions
            .findLockedById(targetPromotionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "promotion_version_not_found",
                        "Promotion version referenced by quote was not found"));
    long customerUseCount =
        redemptions.countByPromotionVersionIdAndCustomerIdAndStatus(
            target.getId(), request.customerId(), RedemptionStatus.APPLIED);
    if (redemption != null && redemption.getStatus() == RedemptionStatus.APPLIED) {
      if (redemption.getPromotionVersionId().equals(target.getId())) {
        customerUseCount = Math.max(0, customerUseCount - 1);
        target.release(redemption.getDiscountAmount());
      } else {
        PromotionVersion previous =
            promotions
                .findLockedById(redemption.getPromotionVersionId())
                .orElseThrow(
                    () ->
                        new ApiException(
                            HttpStatus.NOT_FOUND,
                            "promotion_version_not_found",
                            "Previous promotion version was not found"));
        previous.release(redemption.getDiscountAmount());
      }
      redemption.release();
      outbox.append(
          "PromotionRedemption",
          redemption.getId(),
          "pricing.promotion.released.v1",
          eventPayload(redemption));
    }
    if (target.getPerCustomerQuota() != null
        && customerUseCount >= target.getPerCustomerQuota()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "customer_promotion_quota_exhausted",
          "Customer promotion quota has been exhausted");
    }
    target.redeem(quote.getDiscountAmount());
    if (redemption == null) {
      redemption =
          redemptions.save(
              new PromotionRedemption(
                  target.getId(),
                  reservationId,
                  request.customerId(),
                  quote.getId(),
                  quote.getDiscountAmount()));
    } else {
      redemption.apply(target.getId(), quote.getId(), quote.getDiscountAmount());
    }
    outbox.append(
        "PromotionRedemption",
        redemption.getId(),
        "pricing.promotion.redeemed.v1",
        eventPayload(redemption));
    return promotionSyncView(redemption);
  }

  private void releaseExisting(PromotionRedemption redemption) {
    if (redemption == null || redemption.getStatus() == RedemptionStatus.RELEASED) return;
    PromotionVersion promotion =
        promotions
            .findLockedById(redemption.getPromotionVersionId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "promotion_version_not_found",
                        "Promotion version referenced by redemption was not found"));
    promotion.release(redemption.getDiscountAmount());
    redemption.release();
    outbox.append(
        "PromotionRedemption",
        redemption.getId(),
        "pricing.promotion.released.v1",
        eventPayload(redemption));
  }

  private ComputedQuote compute(QuoteRequest request, Instant now, UUID quoteId) {
    int guests = request.adults() + request.children();
    int nights = Math.toIntExact(ChronoUnit.DAYS.between(request.checkIn(), request.checkOut()));
    List<NightlyPrice> nightlyPrices = new ArrayList<>();
    Instant validUntil = now.plus(quoteTtl);
    BigDecimal roomSubtotal = BigDecimal.ZERO;
    for (LocalDate date = request.checkIn();
        date.isBefore(request.checkOut());
        date = date.plusDays(1)) {
      LocalDate stayDate = date;
      List<BaseRateVersion> candidates =
          baseRates.findApplicable(request.roomTypeId(), stayDate, now);
      if (candidates.isEmpty()) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "base_rate_not_configured",
            "No base rate is configured for " + date);
      }
      BaseRateVersion baseRate = candidates.getFirst();
      validUntil = earliest(validUntil, baseRate.getBookingEffectiveTo(), now);
      BigDecimal current = money(baseRate.getAmount());
      List<AppliedRule> appliedRules = new ArrayList<>();
      Map<UUID, RateRuleVersion> latestRuleVersions = new LinkedHashMap<>();
      rules.findCandidates(request.roomTypeId(), stayDate, now).stream()
          .filter(rule -> rule.applies(stayDate, guests, now))
          .forEach(
              rule ->
                  latestRuleVersions.merge(
                      rule.getRuleKey(),
                      rule,
                      (left, right) -> left.getVersionNo() >= right.getVersionNo() ? left : right));
      List<RateRuleVersion> orderedRules =
          latestRuleVersions.values().stream()
              .sorted(
                  Comparator.comparingInt(RateRuleVersion::getPriority)
                      .thenComparing(RateRuleVersion::getVersionNo, Comparator.reverseOrder()))
              .toList();
      for (RateRuleVersion rule : orderedRules) {
        BigDecimal before = current;
        current = applyRule(current, rule);
        appliedRules.add(
            new AppliedRule(
                rule.getId(),
                rule.getRuleKey(),
                rule.getVersionNo(),
                rule.getName(),
                rule.getPriceMode(),
                rule.getValue(),
                rule.getPriority(),
                before,
                current));
        validUntil = earliest(validUntil, rule.getBookingEffectiveTo(), now);
      }
      BigDecimal lineTotal = money(current.multiply(BigDecimal.valueOf(request.rooms())));
      roomSubtotal = roomSubtotal.add(lineTotal);
      nightlyPrices.add(
          new NightlyPrice(
              date,
              baseRate.getId(),
              baseRate.getVersionNo(),
              baseRate.getAmount(),
              List.copyOf(appliedRules),
              current,
              request.rooms(),
              lineTotal));
    }
    roomSubtotal = money(roomSubtotal);

    PromotionSelection promotionSelection = selectPromotion(request, roomSubtotal, nights, now);
    PromotionDiscount promotionDiscount = promotionSelection.view();
    BigDecimal discount =
        promotionDiscount == null ? BigDecimal.ZERO : promotionDiscount.discountAmount();
    if (promotionSelection.promotion() != null) {
      validUntil = earliest(validUntil, promotionSelection.promotion().getValidTo(), now);
    }
    BigDecimal netSubtotal = money(roomSubtotal.subtract(discount).max(BigDecimal.ZERO));

    List<PolicyVersion> selectedPolicies = selectPolicies(request.roomTypeId(), now);
    List<PolicySnapshot> policySnapshots = new ArrayList<>();
    Map<PolicyType, Map<String, Object>> policyParameters = new EnumMap<>(PolicyType.class);
    for (PolicyVersion policy : selectedPolicies) {
      Map<String, Object> parameters = readMap(policy.getParameters());
      policyParameters.put(policy.getPolicyType(), parameters);
      policySnapshots.add(
          new PolicySnapshot(
              policy.getId(),
              policy.getPolicyKey(),
              policy.getVersionNo(),
              policy.getPolicyType(),
              policy.getScopeType(),
              policy.getGuestDisplayText(),
              parameters));
      validUntil = earliest(validUntil, policy.getEffectiveTo(), now);
    }

    Map<String, Object> feeParameters = policyParameters.getOrDefault(PolicyType.FEES, Map.of());
    BigDecimal taxPercent = decimal(feeParameters, "taxPercent", BigDecimal.ZERO);
    BigDecimal serviceFeePercent = decimal(feeParameters, "serviceFeePercent", BigDecimal.ZERO);
    BigDecimal tax = percentOf(netSubtotal, taxPercent);
    BigDecimal serviceFee = percentOf(netSubtotal, serviceFeePercent);
    BigDecimal total = money(netSubtotal.add(tax).add(serviceFee));
    FeeBreakdown fees = new FeeBreakdown(taxPercent, tax, serviceFeePercent, serviceFee);
    DepositRequirement deposit =
        depositRequirement(policyParameters.get(PolicyType.DEPOSIT), total);

    QuoteView view =
        new QuoteView(
            quoteId,
            now,
            validUntil,
            request.roomTypeId(),
            request.checkIn(),
            request.checkOut(),
            request.rooms(),
            request.adults(),
            request.children(),
            nights,
            currency,
            List.copyOf(nightlyPrices),
            roomSubtotal,
            promotionDiscount,
            discount,
            netSubtotal,
            fees,
            total,
            deposit,
            List.copyOf(policySnapshots));
    return new ComputedQuote(
        view,
        promotionSelection.promotion() == null ? null : promotionSelection.promotion().getId());
  }

  private PromotionSelection selectPromotion(
      QuoteRequest request, BigDecimal subtotal, int nights, Instant now) {
    if (request.promotionCode() == null || request.promotionCode().isBlank()) {
      return new PromotionSelection(null, null);
    }
    List<PromotionVersion> effective =
        promotions.findEffectiveByCode(request.promotionCode().trim(), now);
    for (PromotionVersion promotion : effective) {
      long customerUseCount =
          request.customerId() == null
              ? 0
              : redemptions.countByPromotionVersionIdAndCustomerIdAndStatus(
                  promotion.getId(), request.customerId(), RedemptionStatus.APPLIED);
      if (promotion.isEligible(
          request.roomTypeId(),
          subtotal,
          nights,
          now,
          customerUseCount,
          request.customerId() != null)) {
        BigDecimal discount = money(promotion.calculateDiscount(subtotal));
        if (discount.signum() <= 0) {
          break;
        }
        return new PromotionSelection(
            promotion,
            new PromotionDiscount(
                promotion.getId(),
                promotion.getPromotionKey(),
                promotion.getVersionNo(),
                promotion.getCode(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                discount));
      }
    }
    throw new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "promotion_not_applicable",
        "Promotion does not exist, is inactive/exhausted, or its conditions are not met");
  }

  private List<PolicyVersion> selectPolicies(UUID roomTypeId, Instant now) {
    List<PolicyVersion> result = new ArrayList<>();
    for (PolicyType type : PolicyType.values()) {
      List<PolicyVersion> candidates = policies.findEffective(type, roomTypeId, now);
      if (!candidates.isEmpty()) {
        result.add(candidates.getFirst());
      }
    }
    return result;
  }

  private static BigDecimal applyRule(BigDecimal current, RateRuleVersion rule) {
    BigDecimal result =
        switch (rule.getPriceMode()) {
          case FIXED_NIGHTLY -> rule.getValue();
          case ADDITIVE_SURCHARGE -> current.add(rule.getValue());
          case MULTIPLIER -> current.multiply(rule.getValue());
        };
    if (result.signum() < 0) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "negative_calculated_rate",
          "Price rules produced a negative nightly rate");
    }
    return money(result);
  }

  private static DepositRequirement depositRequirement(
      Map<String, Object> parameters, BigDecimal total) {
    if (parameters == null || !booleanValue(parameters.get("required"))) {
      return new DepositRequirement(false, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    String type = String.valueOf(parameters.get("depositType")).toUpperCase(java.util.Locale.ROOT);
    BigDecimal configured = decimal(parameters, "depositValue", BigDecimal.ZERO);
    BigDecimal amount =
        "PERCENT".equals(type) ? percentOf(total, configured) : money(configured.min(total));
    return new DepositRequirement(true, type, configured, amount);
  }

  private static boolean booleanValue(Object value) {
    return value instanceof Boolean bool
        ? bool
        : value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private static BigDecimal decimal(
      Map<String, Object> parameters, String key, BigDecimal fallback) {
    Object value = parameters.get(key);
    return value == null ? fallback : new BigDecimal(String.valueOf(value));
  }

  private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
    return money(amount.multiply(percent).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static Instant earliest(Instant current, Instant candidate, Instant now) {
    return candidate != null && candidate.isAfter(now) && candidate.isBefore(current)
        ? candidate
        : current;
  }

  private void validateRequest(QuoteRequest request) {
    RoomTypeReference roomType =
        roomTypes
            .findById(request.roomTypeId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "room_type_not_found",
                        "Room type is unknown to Pricing Service"));
    if (!roomType.isActive()) {
      throw new ApiException(HttpStatus.CONFLICT, "room_type_inactive", "Room type is not active");
    }
    if (request.checkIn() == null
        || request.checkOut() == null
        || !request.checkOut().isAfter(request.checkIn())
        || request.checkIn().isBefore(LocalDate.now())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_stay_period",
          "checkOut must be after checkIn and checkIn cannot be in the past");
    }
    long nights = ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
    if (nights > 365) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "stay_too_long", "A quote cannot exceed 365 nights");
    }
    if (request.rooms() < 1 || request.adults() < 1 || request.children() < 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_occupancy",
          "rooms and adults must be positive; children cannot be negative");
    }
  }

  private Map<String, Object> readMap(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot read policy snapshot", exception);
    }
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot serialize quote snapshot", exception);
    }
  }

  private <T> T deserialize(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot read quote snapshot", exception);
    }
  }

  private static PromotionRedemptionView redemptionView(PromotionRedemption redemption) {
    return new PromotionRedemptionView(
        redemption.getId(),
        redemption.getPromotionVersionId(),
        redemption.getReservationId(),
        redemption.getCustomerId(),
        redemption.getQuoteId(),
        redemption.getDiscountAmount(),
        redemption.getStatus(),
        redemption.getCreatedAt(),
        redemption.getReleasedAt());
  }

  private static PromotionSyncView promotionSyncView(PromotionRedemption redemption) {
    return new PromotionSyncView(
        redemption.getReservationId(),
        redemption.getQuoteId(),
        redemption.getStatus() == RedemptionStatus.APPLIED,
        redemption.getPromotionVersionId(),
        redemption.getDiscountAmount(),
        redemption.getStatus().name());
  }

  private static Map<String, Object> eventPayload(PromotionRedemption redemption) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("redemptionId", redemption.getId());
    payload.put("promotionVersionId", redemption.getPromotionVersionId());
    payload.put("reservationId", redemption.getReservationId());
    payload.put("customerId", redemption.getCustomerId());
    payload.put("quoteId", redemption.getQuoteId());
    payload.put("discountAmount", redemption.getDiscountAmount());
    payload.put("status", redemption.getStatus());
    return payload;
  }

  private record ComputedQuote(QuoteView view, UUID promotionVersionId) {}

  private record PromotionSelection(PromotionVersion promotion, PromotionDiscount view) {}
}
