package com.hotelai.pricing;

import static com.hotelai.pricing.PricingDtos.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class PricingAdminService {
  private final RoomTypeReferenceRepository roomTypes;
  private final BaseRateVersionRepository baseRates;
  private final RateRuleVersionRepository rules;
  private final PromotionVersionRepository promotions;
  private final PolicyVersionRepository policies;
  private final OutboxService outbox;
  private final InventoryReadModelPublisher inventoryReadModels;
  private final ObjectMapper objectMapper;
  private final String currency;
  private final BigDecimal minimumNightlyRate;
  private final BigDecimal maximumNightlyRate;

  PricingAdminService(
      RoomTypeReferenceRepository roomTypes,
      BaseRateVersionRepository baseRates,
      RateRuleVersionRepository rules,
      PromotionVersionRepository promotions,
      PolicyVersionRepository policies,
      OutboxService outbox,
      InventoryReadModelPublisher inventoryReadModels,
      ObjectMapper objectMapper,
      @Value("${hotel.pricing.currency}") String currency,
      @Value("${hotel.pricing.minimum-nightly-rate}") BigDecimal minimumNightlyRate,
      @Value("${hotel.pricing.maximum-nightly-rate}") BigDecimal maximumNightlyRate) {
    this.roomTypes = roomTypes;
    this.baseRates = baseRates;
    this.rules = rules;
    this.promotions = promotions;
    this.policies = policies;
    this.outbox = outbox;
    this.inventoryReadModels = inventoryReadModels;
    this.objectMapper = objectMapper;
    this.currency = currency;
    this.minimumNightlyRate = minimumNightlyRate;
    this.maximumNightlyRate = maximumNightlyRate;
  }

  @Transactional
  BaseRateView createBaseRate(BaseRateRequest request, UUID actorId) {
    requireActiveRoomType(request.roomTypeId());
    validateDateRange(request.stayFrom(), request.stayTo(), "stay period");
    validateInstantRange(
        request.bookingEffectiveFrom(), request.bookingEffectiveTo(), "booking effective period");
    requireOutlierConfirmation(request.amount(), request.outlierConfirmed(), "nightly rate");
    int version =
        baseRates
                .findTopByRoomTypeIdOrderByVersionNoDesc(request.roomTypeId())
                .map(BaseRateVersion::getVersionNo)
                .orElse(0)
            + 1;
    BaseRateVersion rate =
        baseRates.save(
            new BaseRateVersion(
                request.roomTypeId(),
                version,
                currency,
                money(request.amount()),
                request.stayFrom(),
                request.stayTo(),
                request.bookingEffectiveFrom(),
                request.bookingEffectiveTo(),
                request.outlierConfirmed(),
                actorId));
    outbox.append(
        "BaseRate",
        rate.getId(),
        "pricing.base-rate.published.v1",
        map(
            "baseRateVersionId",
            rate.getId(),
            "roomTypeId",
            rate.getRoomTypeId(),
            "version",
            rate.getVersionNo(),
            "amount",
            rate.getAmount(),
            "currency",
            rate.getCurrency(),
            "stayFrom",
            rate.getStayFrom(),
            "stayTo",
            rate.getStayTo()));
    inventoryReadModels.publishIfEffective(rate);
    return baseRateView(rate);
  }

  @Transactional(readOnly = true)
  List<BaseRateView> listBaseRates(UUID roomTypeId) {
    return baseRates.findByRoomTypeIdOrderByStayFromAscVersionNoDesc(roomTypeId).stream()
        .map(this::baseRateView)
        .toList();
  }

  @Transactional
  RateRuleView createRateRule(RateRuleRequest request, UUID actorId) {
    requireActiveRoomType(request.roomTypeId());
    validateDateRange(request.stayFrom(), request.stayTo(), "stay period");
    validateInstantRange(
        request.bookingEffectiveFrom(), request.bookingEffectiveTo(), "booking effective period");
    if (request.minGuests() != null
        && request.maxGuests() != null
        && request.maxGuests() < request.minGuests()) {
      throw validation("maxGuests must be greater than or equal to minGuests");
    }
    validateRuleValue(request);
    UUID ruleKey = request.ruleKey() == null ? UUID.randomUUID() : request.ruleKey();
    int version =
        rules
                .findTopByRuleKeyOrderByVersionNoDesc(ruleKey)
                .map(RateRuleVersion::getVersionNo)
                .orElse(0)
            + 1;
    Set<DayOfWeek> days = normalizedDays(request.daysOfWeek());
    List<UUID> conflicts =
        rules.findByRoomTypeIdOrderByPriorityAscVersionNoDesc(request.roomTypeId()).stream()
            .filter(existing -> !existing.getRuleKey().equals(ruleKey))
            .filter(existing -> existing.getPriority() == request.priority())
            .filter(
                existing ->
                    overlaps(
                        existing.getStayFrom(),
                        existing.getStayTo(),
                        request.stayFrom(),
                        request.stayTo()))
            .filter(
                existing ->
                    overlaps(
                        existing.getBookingEffectiveFrom(),
                        existing.getBookingEffectiveTo(),
                        request.bookingEffectiveFrom(),
                        request.bookingEffectiveTo()))
            .filter(existing -> !java.util.Collections.disjoint(existing.decodedDays(), days))
            .filter(
                existing ->
                    guestRangesOverlap(
                        existing.getMinGuests(),
                        existing.getMaxGuests(),
                        request.minGuests(),
                        request.maxGuests()))
            .map(RateRuleVersion::getId)
            .toList();
    if (!conflicts.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "rate_rule_priority_conflict",
          "Another price rule has the same priority and an overlapping scope: " + conflicts);
    }
    RateRuleVersion rule =
        rules.save(
            new RateRuleVersion(
                ruleKey,
                version,
                request.roomTypeId(),
                request.name().trim(),
                request.priceMode(),
                request.value(),
                request.priority(),
                request.stayFrom(),
                request.stayTo(),
                days,
                request.minGuests(),
                request.maxGuests(),
                request.bookingEffectiveFrom(),
                request.bookingEffectiveTo(),
                request.outlierConfirmed(),
                actorId));
    outbox.append(
        "RateRule",
        rule.getId(),
        "pricing.rate-rule.published.v1",
        map(
            "rateRuleVersionId",
            rule.getId(),
            "ruleKey",
            rule.getRuleKey(),
            "version",
            rule.getVersionNo(),
            "roomTypeId",
            rule.getRoomTypeId(),
            "priority",
            rule.getPriority()));
    return rateRuleView(rule);
  }

  @Transactional(readOnly = true)
  List<RateRuleView> listRateRules(UUID roomTypeId) {
    return rules.findByRoomTypeIdOrderByPriorityAscVersionNoDesc(roomTypeId).stream()
        .map(this::rateRuleView)
        .toList();
  }

  @Transactional
  PromotionView createPromotion(PromotionRequest request, UUID actorId) {
    validatePromotion(request);
    Set<UUID> applicableRoomTypes =
        request.applicableRoomTypeIds() == null
            ? Set.of()
            : Set.copyOf(request.applicableRoomTypeIds());
    applicableRoomTypes.forEach(this::requireActiveRoomType);
    UUID promotionKey = request.promotionKey() == null ? UUID.randomUUID() : request.promotionKey();
    List<PromotionVersion> sameCode =
        promotions.findByCodeIgnoreCaseOrderByVersionNoDesc(request.code());
    boolean conflictingCode =
        sameCode.stream()
            .filter(existing -> !existing.getPromotionKey().equals(promotionKey))
            .anyMatch(
                existing ->
                    overlaps(
                        existing.getValidFrom(),
                        existing.getValidTo(),
                        request.validFrom(),
                        request.validTo()));
    if (conflictingCode) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "promotion_code_conflict",
          "Promotion code is already used during an overlapping validity period");
    }
    int version =
        promotions
                .findTopByPromotionKeyOrderByVersionNoDesc(promotionKey)
                .map(PromotionVersion::getVersionNo)
                .orElse(0)
            + 1;
    PromotionVersion promotion =
        promotions.save(
            new PromotionVersion(
                promotionKey,
                version,
                request.code(),
                request.name().trim(),
                request.discountType(),
                money(request.discountValue()),
                nullableMoney(request.maxDiscount()),
                nullableMoney(request.minimumSubtotal()),
                request.minimumNights(),
                request.maximumNights(),
                applicableRoomTypes,
                request.validFrom(),
                request.validTo(),
                request.totalQuota(),
                request.perCustomerQuota(),
                nullableMoney(request.budgetAmount()),
                request.active(),
                actorId));
    outbox.append(
        "Promotion",
        promotion.getId(),
        "pricing.promotion.published.v1",
        map(
            "promotionVersionId",
            promotion.getId(),
            "promotionKey",
            promotion.getPromotionKey(),
            "version",
            promotion.getVersionNo(),
            "code",
            promotion.getCode(),
            "validFrom",
            promotion.getValidFrom(),
            "validTo",
            promotion.getValidTo(),
            "active",
            promotion.isActive()));
    return promotionView(promotion);
  }

  @Transactional(readOnly = true)
  List<PromotionView> listPromotions() {
    return promotions.findAllByOrderByCreatedAtDesc().stream().map(this::promotionView).toList();
  }

  @Transactional
  PromotionView changePromotionStatus(UUID id, boolean active, long expectedVersion) {
    PromotionVersion promotion =
        promotions
            .findById(id)
            .orElseThrow(() -> notFound("promotion_version", "Promotion version was not found"));
    promotion.setActive(active, expectedVersion);
    outbox.append(
        "Promotion",
        promotion.getId(),
        "pricing.promotion.status-changed.v1",
        map(
            "promotionVersionId",
            promotion.getId(),
            "code",
            promotion.getCode(),
            "active",
            active));
    return promotionView(promotion);
  }

  @Transactional
  PolicyView createPolicy(PolicyRequest request, UUID actorId) {
    validatePolicyRequest(request);
    UUID policyKey = request.policyKey() == null ? UUID.randomUUID() : request.policyKey();
    List<PolicyVersion> sameScope =
        policiesForScope(request.policyType(), request.scopeType(), request.scopeId());
    List<UUID> conflicts =
        sameScope.stream()
            .filter(existing -> !existing.getPolicyKey().equals(policyKey))
            .filter(
                existing ->
                    overlaps(
                        existing.getEffectiveFrom(),
                        existing.getEffectiveTo(),
                        request.effectiveFrom(),
                        request.effectiveTo()))
            .map(PolicyVersion::getId)
            .toList();
    if (!conflicts.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "policy_version_overlap",
          "Policy validity overlaps another policy for the same type and scope: " + conflicts);
    }
    int version =
        policies
                .findTopByPolicyKeyOrderByVersionNoDesc(policyKey)
                .map(PolicyVersion::getVersionNo)
                .orElse(0)
            + 1;
    PolicyVersion policy;
    try {
      policy =
          policies.save(
              new PolicyVersion(
                  policyKey,
                  version,
                  request.policyType(),
                  request.scopeType(),
                  request.scopeId(),
                  request.name().trim(),
                  request.guestDisplayText().trim(),
                  objectMapper.writeValueAsString(request.parameters()),
                  request.effectiveFrom(),
                  request.effectiveTo(),
                  actorId));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot serialize policy parameters", exception);
    }
    outbox.append(
        "Policy",
        policy.getId(),
        "pricing.policy.published.v1",
        map(
            "policyVersionId",
            policy.getId(),
            "policyKey",
            policy.getPolicyKey(),
            "version",
            policy.getVersionNo(),
            "policyType",
            policy.getPolicyType(),
            "scopeType",
            policy.getScopeType(),
            "scopeId",
            policy.getScopeId(),
            "effectiveFrom",
            policy.getEffectiveFrom(),
            "effectiveTo",
            policy.getEffectiveTo()));
    inventoryReadModels.publishIfEffective(policy);
    return policyView(policy);
  }

  @Transactional(readOnly = true)
  List<PolicyView> listPolicies() {
    return policies.findAllByOrderByEffectiveFromDesc().stream().map(this::policyView).toList();
  }

  @Transactional(readOnly = true)
  PolicyImpactView previewPolicyImpact(PolicyImpactRequest request) {
    validatePolicyScope(request.scopeType(), request.scopeId());
    validateInstantRange(request.effectiveFrom(), request.effectiveTo(), "policy effective period");
    List<PolicyVersion> sameScope =
        policiesForScope(request.policyType(), request.scopeType(), request.scopeId());
    Instant now = Instant.now();
    List<UUID> conflicts =
        sameScope.stream()
            .filter(
                policy ->
                    overlaps(
                        policy.getEffectiveFrom(),
                        policy.getEffectiveTo(),
                        request.effectiveFrom(),
                        request.effectiveTo()))
            .map(PolicyVersion::getId)
            .toList();
    List<UUID> current =
        sameScope.stream()
            .filter(policy -> policy.activeAt(now))
            .map(PolicyVersion::getId)
            .toList();
    return new PolicyImpactView(
        conflicts,
        current,
        true,
        affectedOperations(request.policyType()),
        "Reservation data belongs to Reservation Service; this preview identifies policy overlap only. "
            + "Existing quote/reservation snapshots remain unchanged by design.");
  }

  private void validatePromotion(PromotionRequest request) {
    validateInstantRange(request.validFrom(), request.validTo(), "promotion validity period");
    if (request.discountType() == DiscountType.PERCENT
        && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
      throw validation("Percentage discount cannot exceed 100");
    }
    if (request.minimumNights() != null
        && request.maximumNights() != null
        && request.maximumNights() < request.minimumNights()) {
      throw validation("maximumNights must be greater than or equal to minimumNights");
    }
    if (request.maxDiscount() != null
        && request.budgetAmount() != null
        && request.maxDiscount().compareTo(request.budgetAmount()) > 0) {
      throw validation("maxDiscount cannot exceed the total promotion budget");
    }
  }

  private void validateRuleValue(RateRuleRequest request) {
    if (request.priceMode() == PriceMode.MULTIPLIER) {
      if (request.value().compareTo(new BigDecimal("10")) > 0 && !request.outlierConfirmed()) {
        throw outlier("Multiplier above 10 requires explicit outlier confirmation");
      }
      return;
    }
    requireOutlierConfirmation(request.value(), request.outlierConfirmed(), "price rule value");
  }

  private void validatePolicyRequest(PolicyRequest request) {
    validatePolicyScope(request.scopeType(), request.scopeId());
    validateInstantRange(request.effectiveFrom(), request.effectiveTo(), "policy effective period");
    validatePolicyParameters(request.policyType(), request.parameters());
  }

  private void validatePolicyScope(PolicyScope scope, UUID scopeId) {
    if (scope == PolicyScope.GLOBAL && scopeId != null) {
      throw validation("GLOBAL policy must not have scopeId");
    }
    if (scope == PolicyScope.ROOM_TYPE) {
      if (scopeId == null) {
        throw validation("ROOM_TYPE policy requires scopeId");
      }
      requireActiveRoomType(scopeId);
    }
  }

  private void validatePolicyParameters(PolicyType type, Map<String, Object> parameters) {
    switch (type) {
      case CANCELLATION -> {
        nonNegative(parameters, "freeCancellationHours", true);
        penalty(parameters, "latePenaltyType", "latePenaltyValue");
      }
      case NO_SHOW -> penalty(parameters, "penaltyType", "penaltyValue");
      case DEPOSIT -> {
        boolean required = booleanValue(parameters, "required", true);
        if (required) {
          String depositType = stringValue(parameters, "depositType", true);
          if (!Set.of("PERCENT", "FIXED").contains(depositType)) {
            throw validation("depositType must be PERCENT or FIXED");
          }
          BigDecimal value = nonNegative(parameters, "depositValue", true);
          if ("PERCENT".equals(depositType) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw validation("depositValue cannot exceed 100 for PERCENT");
          }
        }
      }
      case FEES -> {
        percent(parameters, "taxPercent");
        percent(parameters, "serviceFeePercent");
      }
      case CHECK_IN_OUT, CHILD, SURCHARGE -> {
        if (parameters.isEmpty()) {
          throw validation(type + " policy parameters cannot be empty");
        }
      }
    }
  }

  private void penalty(Map<String, Object> parameters, String typeKey, String valueKey) {
    String penaltyType = stringValue(parameters, typeKey, true);
    if (!Set.of("PERCENT", "FIXED", "NIGHTS").contains(penaltyType)) {
      throw validation(typeKey + " must be PERCENT, FIXED or NIGHTS");
    }
    BigDecimal value = nonNegative(parameters, valueKey, true);
    if ("PERCENT".equals(penaltyType) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw validation(valueKey + " cannot exceed 100 for PERCENT");
    }
  }

  private void percent(Map<String, Object> parameters, String key) {
    BigDecimal value = nonNegative(parameters, key, false);
    if (value != null && value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw validation(key + " cannot exceed 100");
    }
  }

  private static String stringValue(Map<String, Object> values, String key, boolean required) {
    Object raw = values.get(key);
    if (raw == null || String.valueOf(raw).isBlank()) {
      if (required) {
        throw validation(key + " is required");
      }
      return null;
    }
    return String.valueOf(raw).toUpperCase(java.util.Locale.ROOT);
  }

  private static boolean booleanValue(Map<String, Object> values, String key, boolean required) {
    Object raw = values.get(key);
    if (raw == null) {
      if (required) {
        throw validation(key + " is required");
      }
      return false;
    }
    if (raw instanceof Boolean value) {
      return value;
    }
    if ("true".equalsIgnoreCase(String.valueOf(raw))) {
      return true;
    }
    if ("false".equalsIgnoreCase(String.valueOf(raw))) {
      return false;
    }
    throw validation(key + " must be boolean");
  }

  private static BigDecimal nonNegative(Map<String, Object> values, String key, boolean required) {
    Object raw = values.get(key);
    if (raw == null) {
      if (required) {
        throw validation(key + " is required");
      }
      return null;
    }
    try {
      BigDecimal value = new BigDecimal(String.valueOf(raw));
      if (value.signum() < 0) {
        throw validation(key + " must not be negative");
      }
      return value;
    } catch (NumberFormatException exception) {
      throw validation(key + " must be numeric");
    }
  }

  private void requireOutlierConfirmation(BigDecimal value, boolean confirmed, String field) {
    if ((value.compareTo(minimumNightlyRate) < 0 || value.compareTo(maximumNightlyRate) > 0)
        && !confirmed) {
      throw outlier(
          field
              + " is outside configured range "
              + minimumNightlyRate
              + ".."
              + maximumNightlyRate
              + " "
              + currency
              + "; set outlierConfirmed=true after management review");
    }
  }

  private RoomTypeReference requireActiveRoomType(UUID id) {
    RoomTypeReference reference =
        roomTypes
            .findById(id)
            .orElseThrow(
                () ->
                    notFound(
                        "room_type_reference",
                        "Room type is unknown to Pricing Service; synchronize Inventory first"));
    if (!reference.isActive()) {
      throw new ApiException(HttpStatus.CONFLICT, "room_type_inactive", "Room type is not active");
    }
    return reference;
  }

  private List<PolicyVersion> policiesForScope(PolicyType type, PolicyScope scope, UUID scopeId) {
    return scope == PolicyScope.GLOBAL
        ? policies.findByPolicyTypeAndScopeTypeAndScopeIdIsNullOrderByEffectiveFromDesc(type, scope)
        : policies.findByPolicyTypeAndScopeTypeAndScopeIdOrderByEffectiveFromDesc(
            type, scope, scopeId);
  }

  private BaseRateView baseRateView(BaseRateVersion rate) {
    return new BaseRateView(
        rate.getId(),
        rate.getRoomTypeId(),
        rate.getVersionNo(),
        rate.getCurrency(),
        rate.getAmount(),
        rate.getStayFrom(),
        rate.getStayTo(),
        rate.getBookingEffectiveFrom(),
        rate.getBookingEffectiveTo(),
        rate.isOutlierConfirmed(),
        rate.getCreatedAt());
  }

  private RateRuleView rateRuleView(RateRuleVersion rule) {
    return new RateRuleView(
        rule.getId(),
        rule.getRuleKey(),
        rule.getVersionNo(),
        rule.getRoomTypeId(),
        rule.getName(),
        rule.getPriceMode(),
        rule.getValue(),
        rule.getPriority(),
        rule.getStayFrom(),
        rule.getStayTo(),
        rule.decodedDays(),
        rule.getMinGuests(),
        rule.getMaxGuests(),
        rule.getBookingEffectiveFrom(),
        rule.getBookingEffectiveTo(),
        rule.isOutlierConfirmed());
  }

  private PromotionView promotionView(PromotionVersion promotion) {
    return new PromotionView(
        promotion.getId(),
        promotion.getPromotionKey(),
        promotion.getVersionNo(),
        promotion.getCode(),
        promotion.getName(),
        promotion.getDiscountType(),
        promotion.getDiscountValue(),
        promotion.getMaxDiscount(),
        promotion.getMinimumSubtotal(),
        promotion.getMinimumNights(),
        promotion.getMaximumNights(),
        promotion.roomTypeIds(),
        promotion.getValidFrom(),
        promotion.getValidTo(),
        promotion.getTotalQuota(),
        promotion.getPerCustomerQuota(),
        promotion.getBudgetAmount(),
        promotion.getUsedCount(),
        promotion.getBudgetUsed(),
        promotion.isActive(),
        promotion.getLockVersion());
  }

  private PolicyView policyView(PolicyVersion policy) {
    return new PolicyView(
        policy.getId(),
        policy.getPolicyKey(),
        policy.getVersionNo(),
        policy.getPolicyType(),
        policy.getScopeType(),
        policy.getScopeId(),
        policy.getName(),
        policy.getGuestDisplayText(),
        readMap(policy.getParameters()),
        policy.getEffectiveFrom(),
        policy.getEffectiveTo());
  }

  Map<String, Object> readMap(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot read policy parameters", exception);
    }
  }

  private static Set<DayOfWeek> normalizedDays(Set<DayOfWeek> days) {
    return days == null || days.isEmpty() ? Set.of(DayOfWeek.values()) : Set.copyOf(days);
  }

  private static boolean guestRangesOverlap(
      Integer firstMin, Integer firstMax, Integer secondMin, Integer secondMax) {
    int aMin = firstMin == null ? 1 : firstMin;
    int aMax = firstMax == null ? Integer.MAX_VALUE : firstMax;
    int bMin = secondMin == null ? 1 : secondMin;
    int bMax = secondMax == null ? Integer.MAX_VALUE : secondMax;
    return aMin <= bMax && bMin <= aMax;
  }

  private static boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
    return aFrom.isBefore(bTo) && bFrom.isBefore(aTo);
  }

  private static boolean overlaps(Instant aFrom, Instant aTo, Instant bFrom, Instant bTo) {
    Instant max = Instant.parse("9999-12-31T23:59:59Z");
    return aFrom.isBefore(bTo == null ? max : bTo) && bFrom.isBefore(aTo == null ? max : aTo);
  }

  private static void validateDateRange(LocalDate from, LocalDate to, String label) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw validation(label + " must use an exclusive end date after the start date");
    }
  }

  private static void validateInstantRange(Instant from, Instant to, String label) {
    if (from == null || (to != null && !to.isAfter(from))) {
      throw validation(label + " end must be after its start");
    }
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private static BigDecimal nullableMoney(BigDecimal value) {
    return value == null ? null : money(value);
  }

  private static ApiException validation(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
  }

  private static ApiException outlier(String message) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, "price_outlier_confirmation_required", message);
  }

  private static ApiException notFound(String code, String message) {
    return new ApiException(HttpStatus.NOT_FOUND, code + "_not_found", message);
  }

  private static List<String> affectedOperations(PolicyType type) {
    return switch (type) {
      case CANCELLATION -> List.of("reservation cancellation quote", "refund calculation");
      case NO_SHOW -> List.of("no-show charge", "stay closure");
      case DEPOSIT -> List.of("reservation confirmation", "payment request");
      case FEES -> List.of("price quote", "reservation total");
      case CHECK_IN_OUT -> List.of("check-in", "check-out");
      case CHILD -> List.of("occupancy validation", "price quote");
      case SURCHARGE -> List.of("price quote", "stay charge");
    };
  }

  private static Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }
}
