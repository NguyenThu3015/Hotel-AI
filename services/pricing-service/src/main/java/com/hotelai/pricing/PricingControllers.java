package com.hotelai.pricing;

import static com.hotelai.pricing.PricingDtos.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pricing")
class PricingAdminController {
  private final PricingAdminService pricing;
  private final QuoteService quotes;
  private final IdempotencyService idempotency;

  PricingAdminController(
      PricingAdminService pricing, QuoteService quotes, IdempotencyService idempotency) {
    this.pricing = pricing;
    this.quotes = quotes;
    this.idempotency = idempotency;
  }

  @PostMapping("/base-rates")
  @ResponseStatus(HttpStatus.CREATED)
  BaseRateView createBaseRate(
      @RequestHeader("Idempotency-Key") String key,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody BaseRateRequest request) {
    return idempotency.execute(
        key,
        "create-base-rate",
        request,
        BaseRateView.class,
        () -> pricing.createBaseRate(request, actorId(jwt)));
  }

  @GetMapping("/base-rates")
  List<BaseRateView> listBaseRates(@RequestParam UUID roomTypeId) {
    return pricing.listBaseRates(roomTypeId);
  }

  @PostMapping("/rules")
  @ResponseStatus(HttpStatus.CREATED)
  RateRuleView createRateRule(
      @RequestHeader("Idempotency-Key") String key,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RateRuleRequest request) {
    return idempotency.execute(
        key,
        "create-rate-rule",
        request,
        RateRuleView.class,
        () -> pricing.createRateRule(request, actorId(jwt)));
  }

  @GetMapping("/rules")
  List<RateRuleView> listRules(@RequestParam UUID roomTypeId) {
    return pricing.listRateRules(roomTypeId);
  }

  @PostMapping("/simulations")
  QuoteView simulate(@Valid @RequestBody QuoteRequest request) {
    return quotes.simulate(request);
  }

  @PostMapping("/promotions")
  @ResponseStatus(HttpStatus.CREATED)
  PromotionView createPromotion(
      @RequestHeader("Idempotency-Key") String key,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PromotionRequest request) {
    return idempotency.execute(
        key,
        "create-promotion",
        request,
        PromotionView.class,
        () -> pricing.createPromotion(request, actorId(jwt)));
  }

  @GetMapping("/promotions")
  List<PromotionView> listPromotions() {
    return pricing.listPromotions();
  }

  @PatchMapping("/promotions/{id}/status")
  PromotionView changePromotionStatus(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody PromotionStatusRequest request) {
    long expectedVersion = entityVersion(ifMatch);
    return idempotency.execute(
        key,
        "change-promotion-status:" + id,
        Map.of("active", request.active(), "entityVersion", expectedVersion),
        PromotionView.class,
        () -> pricing.changePromotionStatus(id, request.active(), expectedVersion));
  }

  @PostMapping("/policies")
  @ResponseStatus(HttpStatus.CREATED)
  PolicyView createPolicy(
      @RequestHeader("Idempotency-Key") String key,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PolicyRequest request) {
    return idempotency.execute(
        key,
        "create-policy",
        request,
        PolicyView.class,
        () -> pricing.createPolicy(request, actorId(jwt)));
  }

  @GetMapping("/policies")
  List<PolicyView> listPolicies() {
    return pricing.listPolicies();
  }

  @PostMapping("/policies/impact-preview")
  PolicyImpactView previewPolicyImpact(@Valid @RequestBody PolicyImpactRequest request) {
    return pricing.previewPolicyImpact(request);
  }

  private static UUID actorId(Jwt jwt) {
    if (jwt == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "JWT is required");
    }
    String identityId = jwt.getClaimAsString("uid");
    try {
      return UUID.fromString(identityId == null ? jwt.getSubject() : identityId);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "invalid_identity_claim",
          "JWT uid claim must contain an identity UUID");
    }
  }

  private static long entityVersion(String ifMatch) {
    try {
      String normalized = ifMatch.trim().replace("W/", "").replace("\"", "");
      return Long.parseLong(normalized);
    } catch (Exception exception) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_if_match",
          "If-Match must contain the numeric entity version returned by the API");
    }
  }
}

@RestController
@RequestMapping("/internal/v1/pricing")
class InternalPricingController {
  private final QuoteService quotes;
  private final IdempotencyService idempotency;
  private final RoomTypeProjectionService roomTypes;

  InternalPricingController(
      QuoteService quotes, IdempotencyService idempotency, RoomTypeProjectionService roomTypes) {
    this.quotes = quotes;
    this.idempotency = idempotency;
    this.roomTypes = roomTypes;
  }

  @PostMapping("/quotes")
  @ResponseStatus(HttpStatus.CREATED)
  QuoteView quote(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody QuoteRequest request) {
    return quotes.createQuote(key, request);
  }

  @PostMapping("/promotion-redemptions")
  @ResponseStatus(HttpStatus.CREATED)
  PromotionRedemptionView redeem(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PromotionRedemptionRequest request) {
    return idempotency.execute(
        key,
        "redeem-promotion",
        request,
        PromotionRedemptionView.class,
        () -> quotes.redeem(request));
  }

  @PutMapping("/promotion-redemptions/{reservationId}")
  PromotionSyncView synchronizePromotion(
      @PathVariable UUID reservationId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PromotionSyncRequest request) {
    return idempotency.execute(
        key,
        "synchronize-promotion:" + reservationId,
        request,
        PromotionSyncView.class,
        () -> quotes.synchronizePromotion(reservationId, request));
  }

  @PostMapping("/promotion-redemptions/{reservationId}/release")
  PromotionRedemptionView release(
      @PathVariable UUID reservationId, @RequestHeader("Idempotency-Key") String key) {
    return idempotency.execute(
        key,
        "release-promotion:" + reservationId,
        Map.of("reservationId", reservationId),
        PromotionRedemptionView.class,
        () -> quotes.release(reservationId));
  }

  @PutMapping("/room-types/{roomTypeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void synchronizeRoomType(
      @PathVariable UUID roomTypeId, @Valid @RequestBody RoomTypeSyncRequest request) {
    roomTypes.synchronize(
        roomTypeId, request.code(), request.name(), request.active(), request.sourceVersion());
  }
}
