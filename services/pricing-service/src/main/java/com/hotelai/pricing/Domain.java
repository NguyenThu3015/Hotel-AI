package com.hotelai.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

enum PriceMode {
  FIXED_NIGHTLY,
  ADDITIVE_SURCHARGE,
  MULTIPLIER
}

enum DiscountType {
  PERCENT,
  FIXED
}

enum PolicyType {
  CANCELLATION,
  NO_SHOW,
  DEPOSIT,
  FEES,
  CHECK_IN_OUT,
  CHILD,
  SURCHARGE
}

enum PolicyScope {
  GLOBAL,
  ROOM_TYPE
}

enum RedemptionStatus {
  APPLIED,
  RELEASED
}

@Entity
@Table(name = "room_type_references")
class RoomTypeReference {
  @Id private UUID id;

  @Column(nullable = false, length = 80)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "source_version", nullable = false)
  private long sourceVersion;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected RoomTypeReference() {}

  RoomTypeReference(UUID id, String code, String name, boolean active, long sourceVersion) {
    this.id = id;
    synchronize(code, name, active, sourceVersion);
  }

  void synchronize(String code, String name, boolean active, long sourceVersion) {
    if (sourceVersion < this.sourceVersion) {
      return;
    }
    this.code = code;
    this.name = name;
    this.active = active;
    this.sourceVersion = sourceVersion;
    this.updatedAt = Instant.now();
  }

  UUID getId() {
    return id;
  }

  String getCode() {
    return code;
  }

  String getName() {
    return name;
  }

  boolean isActive() {
    return active;
  }
}

@Entity
@Table(name = "base_rate_versions")
class BaseRateVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_type_id", nullable = false, updatable = false)
  private UUID roomTypeId;

  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(nullable = false, precision = 19, scale = 2, updatable = false)
  private BigDecimal amount;

  @Column(name = "stay_from", nullable = false, updatable = false)
  private LocalDate stayFrom;

  @Column(name = "stay_to", nullable = false, updatable = false)
  private LocalDate stayTo;

  @Column(name = "booking_effective_from", nullable = false, updatable = false)
  private Instant bookingEffectiveFrom;

  @Column(name = "booking_effective_to", updatable = false)
  private Instant bookingEffectiveTo;

  @Column(name = "outlier_confirmed", nullable = false, updatable = false)
  private boolean outlierConfirmed;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "inventory_snapshot_published_at")
  private Instant inventorySnapshotPublishedAt;

  @Version private long lockVersion;

  protected BaseRateVersion() {}

  BaseRateVersion(
      UUID roomTypeId,
      int versionNo,
      String currency,
      BigDecimal amount,
      LocalDate stayFrom,
      LocalDate stayTo,
      Instant bookingEffectiveFrom,
      Instant bookingEffectiveTo,
      boolean outlierConfirmed,
      UUID createdBy) {
    this.roomTypeId = roomTypeId;
    this.versionNo = versionNo;
    this.currency = currency;
    this.amount = amount;
    this.stayFrom = stayFrom;
    this.stayTo = stayTo;
    this.bookingEffectiveFrom = bookingEffectiveFrom;
    this.bookingEffectiveTo = bookingEffectiveTo;
    this.outlierConfirmed = outlierConfirmed;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  boolean applies(LocalDate stayDate, Instant bookedAt) {
    return !stayDate.isBefore(stayFrom)
        && stayDate.isBefore(stayTo)
        && !bookedAt.isBefore(bookingEffectiveFrom)
        && (bookingEffectiveTo == null || bookedAt.isBefore(bookingEffectiveTo));
  }

  UUID getId() {
    return id;
  }

  UUID getRoomTypeId() {
    return roomTypeId;
  }

  int getVersionNo() {
    return versionNo;
  }

  String getCurrency() {
    return currency;
  }

  BigDecimal getAmount() {
    return amount;
  }

  LocalDate getStayFrom() {
    return stayFrom;
  }

  LocalDate getStayTo() {
    return stayTo;
  }

  Instant getBookingEffectiveFrom() {
    return bookingEffectiveFrom;
  }

  Instant getBookingEffectiveTo() {
    return bookingEffectiveTo;
  }

  boolean isOutlierConfirmed() {
    return outlierConfirmed;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void markInventorySnapshotPublished() {
    inventorySnapshotPublishedAt = Instant.now();
  }

  boolean isInventorySnapshotPending() {
    return inventorySnapshotPublishedAt == null;
  }
}

@Entity
@Table(name = "rate_rule_versions")
class RateRuleVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "rule_key", nullable = false, updatable = false)
  private UUID ruleKey;

  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  @Column(name = "room_type_id", nullable = false, updatable = false)
  private UUID roomTypeId;

  @Column(nullable = false, length = 160, updatable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "price_mode", nullable = false, length = 30, updatable = false)
  private PriceMode priceMode;

  @Column(name = "rule_value", nullable = false, precision = 19, scale = 4, updatable = false)
  private BigDecimal value;

  @Column(nullable = false, updatable = false)
  private int priority;

  @Column(name = "stay_from", nullable = false, updatable = false)
  private LocalDate stayFrom;

  @Column(name = "stay_to", nullable = false, updatable = false)
  private LocalDate stayTo;

  @Column(name = "days_of_week", nullable = false, length = 96, updatable = false)
  private String daysOfWeek;

  @Column(name = "min_guests", updatable = false)
  private Integer minGuests;

  @Column(name = "max_guests", updatable = false)
  private Integer maxGuests;

  @Column(name = "booking_effective_from", nullable = false, updatable = false)
  private Instant bookingEffectiveFrom;

  @Column(name = "booking_effective_to", updatable = false)
  private Instant bookingEffectiveTo;

  @Column(name = "outlier_confirmed", nullable = false, updatable = false)
  private boolean outlierConfirmed;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long lockVersion;

  protected RateRuleVersion() {}

  RateRuleVersion(
      UUID ruleKey,
      int versionNo,
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
      boolean outlierConfirmed,
      UUID createdBy) {
    this.ruleKey = ruleKey;
    this.versionNo = versionNo;
    this.roomTypeId = roomTypeId;
    this.name = name;
    this.priceMode = priceMode;
    this.value = value;
    this.priority = priority;
    this.stayFrom = stayFrom;
    this.stayTo = stayTo;
    this.daysOfWeek = encodeDays(daysOfWeek);
    this.minGuests = minGuests;
    this.maxGuests = maxGuests;
    this.bookingEffectiveFrom = bookingEffectiveFrom;
    this.bookingEffectiveTo = bookingEffectiveTo;
    this.outlierConfirmed = outlierConfirmed;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  boolean applies(LocalDate stayDate, int guests, Instant bookedAt) {
    return !stayDate.isBefore(stayFrom)
        && stayDate.isBefore(stayTo)
        && decodedDays().contains(stayDate.getDayOfWeek())
        && (minGuests == null || guests >= minGuests)
        && (maxGuests == null || guests <= maxGuests)
        && !bookedAt.isBefore(bookingEffectiveFrom)
        && (bookingEffectiveTo == null || bookedAt.isBefore(bookingEffectiveTo));
  }

  Set<DayOfWeek> decodedDays() {
    if (daysOfWeek == null || daysOfWeek.isBlank()) {
      return Set.of(DayOfWeek.values());
    }
    return Arrays.stream(daysOfWeek.split(","))
        .map(DayOfWeek::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String encodeDays(Set<DayOfWeek> values) {
    if (values == null || values.isEmpty() || values.size() == DayOfWeek.values().length) {
      return "";
    }
    return values.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
  }

  UUID getId() {
    return id;
  }

  UUID getRuleKey() {
    return ruleKey;
  }

  int getVersionNo() {
    return versionNo;
  }

  UUID getRoomTypeId() {
    return roomTypeId;
  }

  String getName() {
    return name;
  }

  PriceMode getPriceMode() {
    return priceMode;
  }

  BigDecimal getValue() {
    return value;
  }

  int getPriority() {
    return priority;
  }

  LocalDate getStayFrom() {
    return stayFrom;
  }

  LocalDate getStayTo() {
    return stayTo;
  }

  Integer getMinGuests() {
    return minGuests;
  }

  Integer getMaxGuests() {
    return maxGuests;
  }

  Instant getBookingEffectiveFrom() {
    return bookingEffectiveFrom;
  }

  Instant getBookingEffectiveTo() {
    return bookingEffectiveTo;
  }

  boolean isOutlierConfirmed() {
    return outlierConfirmed;
  }
}

@Entity
@Table(name = "promotion_versions")
class PromotionVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "promotion_key", nullable = false, updatable = false)
  private UUID promotionKey;

  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  @Column(nullable = false, length = 64, updatable = false)
  private String code;

  @Column(nullable = false, length = 180, updatable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false, length = 20, updatable = false)
  private DiscountType discountType;

  @Column(name = "discount_value", nullable = false, precision = 19, scale = 2, updatable = false)
  private BigDecimal discountValue;

  @Column(name = "max_discount", precision = 19, scale = 2, updatable = false)
  private BigDecimal maxDiscount;

  @Column(name = "minimum_subtotal", precision = 19, scale = 2, updatable = false)
  private BigDecimal minimumSubtotal;

  @Column(name = "minimum_nights", updatable = false)
  private Integer minimumNights;

  @Column(name = "maximum_nights", updatable = false)
  private Integer maximumNights;

  @Column(
      name = "applicable_room_type_ids",
      nullable = false,
      columnDefinition = "text",
      updatable = false)
  private String applicableRoomTypeIds;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private Instant validFrom;

  @Column(name = "valid_to", nullable = false, updatable = false)
  private Instant validTo;

  @Column(name = "total_quota", updatable = false)
  private Integer totalQuota;

  @Column(name = "per_customer_quota", updatable = false)
  private Integer perCustomerQuota;

  @Column(name = "budget_amount", precision = 19, scale = 2, updatable = false)
  private BigDecimal budgetAmount;

  @Column(name = "used_count", nullable = false)
  private int usedCount;

  @Column(name = "budget_used", nullable = false, precision = 19, scale = 2)
  private BigDecimal budgetUsed = BigDecimal.ZERO;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "manually_paused", nullable = false)
  private boolean manuallyPaused;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long lockVersion;

  protected PromotionVersion() {}

  PromotionVersion(
      UUID promotionKey,
      int versionNo,
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
      boolean active,
      UUID createdBy) {
    this.promotionKey = promotionKey;
    this.versionNo = versionNo;
    this.code = code.toUpperCase(java.util.Locale.ROOT);
    this.name = name;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.maxDiscount = maxDiscount;
    this.minimumSubtotal = minimumSubtotal;
    this.minimumNights = minimumNights;
    this.maximumNights = maximumNights;
    this.applicableRoomTypeIds = encodeRoomTypes(applicableRoomTypeIds);
    this.validFrom = validFrom;
    this.validTo = validTo;
    this.totalQuota = totalQuota;
    this.perCustomerQuota = perCustomerQuota;
    this.budgetAmount = budgetAmount;
    this.active = active;
    this.manuallyPaused = !active;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  boolean isEligible(
      UUID roomTypeId,
      BigDecimal subtotal,
      int nights,
      Instant at,
      long customerUseCount,
      boolean customerKnown) {
    return active
        && !at.isBefore(validFrom)
        && at.isBefore(validTo)
        && (roomTypeIds().isEmpty() || roomTypeIds().contains(roomTypeId))
        && (minimumSubtotal == null || subtotal.compareTo(minimumSubtotal) >= 0)
        && (minimumNights == null || nights >= minimumNights)
        && (maximumNights == null || nights <= maximumNights)
        && (totalQuota == null || usedCount < totalQuota)
        && (perCustomerQuota == null || (customerKnown && customerUseCount < perCustomerQuota))
        && (budgetAmount == null || budgetUsed.compareTo(budgetAmount) < 0);
  }

  BigDecimal calculateDiscount(BigDecimal subtotal) {
    BigDecimal discount =
        discountType == DiscountType.PERCENT
            ? subtotal.multiply(discountValue).divide(BigDecimal.valueOf(100))
            : discountValue;
    if (maxDiscount != null) {
      discount = discount.min(maxDiscount);
    }
    if (budgetAmount != null) {
      discount = discount.min(budgetAmount.subtract(budgetUsed).max(BigDecimal.ZERO));
    }
    return discount.min(subtotal).max(BigDecimal.ZERO);
  }

  void redeem(BigDecimal amount) {
    if (!active || (totalQuota != null && usedCount >= totalQuota)) {
      throw new ApiException(
          org.springframework.http.HttpStatus.CONFLICT,
          "promotion_exhausted",
          "Promotion quota has been exhausted");
    }
    if (budgetAmount != null && budgetUsed.add(amount).compareTo(budgetAmount) > 0) {
      throw new ApiException(
          org.springframework.http.HttpStatus.CONFLICT,
          "promotion_budget_exhausted",
          "Promotion budget has been exhausted");
    }
    usedCount++;
    budgetUsed = budgetUsed.add(amount);
    if ((totalQuota != null && usedCount >= totalQuota)
        || (budgetAmount != null && budgetUsed.compareTo(budgetAmount) >= 0)) {
      active = false;
    }
  }

  void release(BigDecimal amount) {
    usedCount = Math.max(0, usedCount - 1);
    budgetUsed = budgetUsed.subtract(amount).max(BigDecimal.ZERO);
    if (!manuallyPaused && Instant.now().isBefore(validTo)) {
      active = true;
    }
  }

  void setActive(boolean active, long expectedVersion) {
    if (lockVersion != expectedVersion) {
      throw new ApiException(
          org.springframework.http.HttpStatus.CONFLICT,
          "stale_version",
          "Promotion version is stale; reload and retry");
    }
    if (active
        && ((totalQuota != null && usedCount >= totalQuota)
            || (budgetAmount != null && budgetUsed.compareTo(budgetAmount) >= 0))) {
      throw new ApiException(
          org.springframework.http.HttpStatus.CONFLICT,
          "promotion_exhausted",
          "Promotion cannot be activated because its quota or budget is exhausted");
    }
    this.manuallyPaused = !active;
    this.active = active;
  }

  Set<UUID> roomTypeIds() {
    if (applicableRoomTypeIds == null || applicableRoomTypeIds.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(applicableRoomTypeIds.split(","))
        .map(UUID::fromString)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String encodeRoomTypes(Set<UUID> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return values.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
  }

  UUID getId() {
    return id;
  }

  UUID getPromotionKey() {
    return promotionKey;
  }

  int getVersionNo() {
    return versionNo;
  }

  String getCode() {
    return code;
  }

  String getName() {
    return name;
  }

  DiscountType getDiscountType() {
    return discountType;
  }

  BigDecimal getDiscountValue() {
    return discountValue;
  }

  BigDecimal getMaxDiscount() {
    return maxDiscount;
  }

  BigDecimal getMinimumSubtotal() {
    return minimumSubtotal;
  }

  Integer getMinimumNights() {
    return minimumNights;
  }

  Integer getMaximumNights() {
    return maximumNights;
  }

  Instant getValidFrom() {
    return validFrom;
  }

  Instant getValidTo() {
    return validTo;
  }

  Integer getTotalQuota() {
    return totalQuota;
  }

  Integer getPerCustomerQuota() {
    return perCustomerQuota;
  }

  BigDecimal getBudgetAmount() {
    return budgetAmount;
  }

  int getUsedCount() {
    return usedCount;
  }

  BigDecimal getBudgetUsed() {
    return budgetUsed;
  }

  boolean isActive() {
    return active;
  }

  long getLockVersion() {
    return lockVersion;
  }
}

@Entity
@Table(name = "policy_versions")
class PolicyVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "policy_key", nullable = false, updatable = false)
  private UUID policyKey;

  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "policy_type", nullable = false, length = 30, updatable = false)
  private PolicyType policyType;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope_type", nullable = false, length = 20, updatable = false)
  private PolicyScope scopeType;

  @Column(name = "scope_id", updatable = false)
  private UUID scopeId;

  @Column(nullable = false, length = 180, updatable = false)
  private String name;

  @Column(
      name = "guest_display_text",
      nullable = false,
      columnDefinition = "text",
      updatable = false)
  private String guestDisplayText;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private String parameters;

  @Column(name = "effective_from", nullable = false, updatable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to", updatable = false)
  private Instant effectiveTo;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "inventory_snapshot_published_at")
  private Instant inventorySnapshotPublishedAt;

  @Version private long lockVersion;

  protected PolicyVersion() {}

  PolicyVersion(
      UUID policyKey,
      int versionNo,
      PolicyType policyType,
      PolicyScope scopeType,
      UUID scopeId,
      String name,
      String guestDisplayText,
      String parameters,
      Instant effectiveFrom,
      Instant effectiveTo,
      UUID createdBy) {
    this.policyKey = policyKey;
    this.versionNo = versionNo;
    this.policyType = policyType;
    this.scopeType = scopeType;
    this.scopeId = scopeId;
    this.name = name;
    this.guestDisplayText = guestDisplayText;
    this.parameters = parameters;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  boolean activeAt(Instant at) {
    return !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
  }

  UUID getId() {
    return id;
  }

  UUID getPolicyKey() {
    return policyKey;
  }

  int getVersionNo() {
    return versionNo;
  }

  PolicyType getPolicyType() {
    return policyType;
  }

  PolicyScope getScopeType() {
    return scopeType;
  }

  UUID getScopeId() {
    return scopeId;
  }

  String getName() {
    return name;
  }

  String getGuestDisplayText() {
    return guestDisplayText;
  }

  String getParameters() {
    return parameters;
  }

  Instant getEffectiveFrom() {
    return effectiveFrom;
  }

  Instant getEffectiveTo() {
    return effectiveTo;
  }

  void markInventorySnapshotPublished() {
    inventorySnapshotPublishedAt = Instant.now();
  }

  boolean isInventorySnapshotPending() {
    return inventorySnapshotPublishedAt == null;
  }
}

@Entity
@Table(name = "price_quotes")
class PriceQuote {
  @Id private UUID id;

  @Column(
      name = "idempotency_key",
      nullable = false,
      unique = true,
      updatable = false,
      length = 160)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Column(name = "room_type_id", nullable = false, updatable = false)
  private UUID roomTypeId;

  @Column(name = "customer_id", updatable = false)
  private UUID customerId;

  @Column(name = "promotion_version_id", updatable = false)
  private UUID promotionVersionId;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2, updatable = false)
  private BigDecimal discountAmount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "response_snapshot",
      nullable = false,
      columnDefinition = "jsonb",
      updatable = false)
  private String responseSnapshot;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  protected PriceQuote() {}

  PriceQuote(
      UUID id,
      String idempotencyKey,
      String requestHash,
      UUID roomTypeId,
      UUID customerId,
      UUID promotionVersionId,
      BigDecimal discountAmount,
      String responseSnapshot,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.roomTypeId = roomTypeId;
    this.customerId = customerId;
    this.promotionVersionId = promotionVersionId;
    this.discountAmount = discountAmount;
    this.responseSnapshot = responseSnapshot;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  UUID getId() {
    return id;
  }

  String getIdempotencyKey() {
    return idempotencyKey;
  }

  String getRequestHash() {
    return requestHash;
  }

  UUID getCustomerId() {
    return customerId;
  }

  UUID getPromotionVersionId() {
    return promotionVersionId;
  }

  BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  String getResponseSnapshot() {
    return responseSnapshot;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }
}

@Entity
@Table(name = "promotion_redemptions")
class PromotionRedemption {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "promotion_version_id", nullable = false)
  private UUID promotionVersionId;

  @Column(name = "reservation_id", nullable = false, unique = true, updatable = false)
  private UUID reservationId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "quote_id", nullable = false)
  private UUID quoteId;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal discountAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RedemptionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Version private long version;

  protected PromotionRedemption() {}

  PromotionRedemption(
      UUID promotionVersionId,
      UUID reservationId,
      UUID customerId,
      UUID quoteId,
      BigDecimal discountAmount) {
    this.promotionVersionId = promotionVersionId;
    this.reservationId = reservationId;
    this.customerId = customerId;
    this.quoteId = quoteId;
    this.discountAmount = discountAmount;
    this.status = RedemptionStatus.APPLIED;
    this.createdAt = Instant.now();
  }

  void release() {
    if (status == RedemptionStatus.RELEASED) {
      return;
    }
    status = RedemptionStatus.RELEASED;
    releasedAt = Instant.now();
  }

  void apply(UUID promotionVersionId, UUID quoteId, BigDecimal discountAmount) {
    this.promotionVersionId = promotionVersionId;
    this.quoteId = quoteId;
    this.discountAmount = discountAmount;
    this.status = RedemptionStatus.APPLIED;
    this.releasedAt = null;
  }

  UUID getId() {
    return id;
  }

  UUID getPromotionVersionId() {
    return promotionVersionId;
  }

  UUID getReservationId() {
    return reservationId;
  }

  UUID getCustomerId() {
    return customerId;
  }

  UUID getQuoteId() {
    return quoteId;
  }

  BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  RedemptionStatus getStatus() {
    return status;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getReleasedAt() {
    return releasedAt;
  }
}

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100, updatable = false)
  private String operation;

  @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
  private String requestHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_json", nullable = false, columnDefinition = "jsonb", updatable = false)
  private String responseJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected IdempotencyRecord() {}

  IdempotencyRecord(
      String operation, String idempotencyKey, String requestHash, String responseJson) {
    this.operation = operation;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.responseJson = responseJson;
    this.createdAt = Instant.now();
  }

  String getRequestHash() {
    return requestHash;
  }

  String getResponseJson() {
    return responseJson;
  }
}

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 80, updatable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 140, updatable = false)
  private String eventType;

  @Column(name = "correlation_id", nullable = false, updatable = false)
  private UUID correlationId;

  @Column(name = "schema_version", nullable = false, updatable = false)
  private int schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private String payload;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "publish_attempts", nullable = false)
  private int publishAttempts;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Version private long version;

  protected OutboxEvent() {}

  OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
    this.id = UUID.randomUUID();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.correlationId = CorrelationContext.current();
    this.schemaVersion = 1;
    this.payload = payload;
    this.occurredAt = Instant.now();
  }

  void markPublished() {
    publishedAt = Instant.now();
    publishAttempts++;
    lastError = null;
  }

  void markFailed(String error) {
    publishAttempts++;
    lastError =
        error == null
            ? "Unknown publish failure"
            : error.substring(0, Math.min(500, error.length()));
  }

  UUID getId() {
    return id;
  }

  String getAggregateType() {
    return aggregateType;
  }

  UUID getAggregateId() {
    return aggregateId;
  }

  String getEventType() {
    return eventType;
  }

  UUID getCorrelationId() {
    return correlationId;
  }

  int getSchemaVersion() {
    return schemaVersion;
  }

  String getPayload() {
    return payload;
  }

  Instant getOccurredAt() {
    return occurredAt;
  }
}

@Entity
@Table(name = "inbox_events")
class InboxEvent {
  @Id
  @Column(name = "event_id", length = 120)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 140)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected InboxEvent() {}

  InboxEvent(String eventId, String eventType) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = Instant.now();
  }
}
