package com.hotelai.pricing;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomTypeReferenceRepository extends JpaRepository<RoomTypeReference, UUID> {
  List<RoomTypeReference> findByActiveTrueOrderByCodeAsc();
}

interface BaseRateVersionRepository extends JpaRepository<BaseRateVersion, UUID> {
  Optional<BaseRateVersion> findTopByRoomTypeIdOrderByVersionNoDesc(UUID roomTypeId);

  List<BaseRateVersion> findByRoomTypeIdOrderByStayFromAscVersionNoDesc(UUID roomTypeId);

  @Query(
      """
      select rate from BaseRateVersion rate
      where rate.inventorySnapshotPublishedAt is null
        and rate.bookingEffectiveFrom <= :at
        and (rate.bookingEffectiveTo is null or rate.bookingEffectiveTo > :at)
      order by rate.bookingEffectiveFrom, rate.versionNo
      """)
  List<BaseRateVersion> findInventorySnapshotsDue(
      @Param("at") Instant at, Pageable pageable);

  @Query(
      """
      select rate from BaseRateVersion rate
      where rate.roomTypeId = :roomTypeId
        and rate.stayFrom <= :stayDate and rate.stayTo > :stayDate
        and rate.bookingEffectiveFrom <= :at
        and (rate.bookingEffectiveTo is null or rate.bookingEffectiveTo > :at)
      order by rate.versionNo desc
      """)
  List<BaseRateVersion> findApplicable(
      @Param("roomTypeId") UUID roomTypeId,
      @Param("stayDate") LocalDate stayDate,
      @Param("at") Instant at);
}

interface RateRuleVersionRepository extends JpaRepository<RateRuleVersion, UUID> {
  Optional<RateRuleVersion> findTopByRuleKeyOrderByVersionNoDesc(UUID ruleKey);

  List<RateRuleVersion> findByRoomTypeIdOrderByPriorityAscVersionNoDesc(UUID roomTypeId);

  @Query(
      """
      select rule from RateRuleVersion rule
      where rule.roomTypeId = :roomTypeId
        and rule.stayFrom <= :stayDate and rule.stayTo > :stayDate
        and rule.bookingEffectiveFrom <= :at
        and (rule.bookingEffectiveTo is null or rule.bookingEffectiveTo > :at)
      order by rule.priority asc, rule.versionNo desc
      """)
  List<RateRuleVersion> findCandidates(
      @Param("roomTypeId") UUID roomTypeId,
      @Param("stayDate") LocalDate stayDate,
      @Param("at") Instant at);
}

interface PromotionVersionRepository extends JpaRepository<PromotionVersion, UUID> {
  Optional<PromotionVersion> findTopByPromotionKeyOrderByVersionNoDesc(UUID promotionKey);

  List<PromotionVersion> findByCodeIgnoreCaseOrderByVersionNoDesc(String code);

  List<PromotionVersion> findAllByOrderByCreatedAtDesc();

  @Query(
      """
      select promotion from PromotionVersion promotion
      where upper(promotion.code) = upper(:code)
        and promotion.validFrom <= :at and promotion.validTo > :at
      order by promotion.versionNo desc
      """)
  List<PromotionVersion> findEffectiveByCode(@Param("code") String code, @Param("at") Instant at);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select promotion from PromotionVersion promotion where promotion.id = :id")
  Optional<PromotionVersion> findLockedById(@Param("id") UUID id);
}

interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {
  Optional<PolicyVersion> findTopByPolicyKeyOrderByVersionNoDesc(UUID policyKey);

  List<PolicyVersion> findAllByOrderByEffectiveFromDesc();

  @Query(
      """
      select policy from PolicyVersion policy
      where policy.inventorySnapshotPublishedAt is null
        and policy.effectiveFrom <= :at
        and (policy.effectiveTo is null or policy.effectiveTo > :at)
      order by policy.effectiveFrom, policy.versionNo
      """)
  List<PolicyVersion> findInventorySnapshotsDue(
      @Param("at") Instant at, Pageable pageable);

  List<PolicyVersion> findByPolicyTypeAndScopeTypeAndScopeIdOrderByEffectiveFromDesc(
      PolicyType policyType, PolicyScope scopeType, UUID scopeId);

  List<PolicyVersion> findByPolicyTypeAndScopeTypeAndScopeIdIsNullOrderByEffectiveFromDesc(
      PolicyType policyType, PolicyScope scopeType);

  @Query(
      """
      select policy from PolicyVersion policy
      where policy.policyType = :type
        and policy.effectiveFrom <= :at
        and (policy.effectiveTo is null or policy.effectiveTo > :at)
        and ((policy.scopeType = com.hotelai.pricing.PolicyScope.ROOM_TYPE and policy.scopeId = :roomTypeId)
          or policy.scopeType = com.hotelai.pricing.PolicyScope.GLOBAL)
      order by case when policy.scopeType = com.hotelai.pricing.PolicyScope.ROOM_TYPE then 0 else 1 end,
        policy.versionNo desc
      """)
  List<PolicyVersion> findEffective(
      @Param("type") PolicyType type,
      @Param("roomTypeId") UUID roomTypeId,
      @Param("at") Instant at);
}

interface PriceQuoteRepository extends JpaRepository<PriceQuote, UUID> {
  @Query(
      value =
          """
          select 1
          from (select pg_advisory_xact_lock(
                  hashtextextended(cast(:key as text), 0))) as idempotency_lock
          """,
      nativeQuery = true)
  int lockIdempotencyKey(@Param("key") String key);

  Optional<PriceQuote> findByIdempotencyKey(String idempotencyKey);
}

interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, UUID> {
  Optional<PromotionRedemption> findByReservationId(UUID reservationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select redemption from PromotionRedemption redemption where redemption.reservationId = :reservationId")
  Optional<PromotionRedemption> findLockedByReservationId(@Param("reservationId") UUID reservationId);

  long countByPromotionVersionIdAndCustomerIdAndStatus(
      UUID promotionVersionId, UUID customerId, RedemptionStatus status);
}

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
  @Query(
      value =
          """
          select 1
          from (select pg_advisory_xact_lock(
                  hashtextextended(cast(:key as text), 0))) as idempotency_lock
          """,
      nativeQuery = true)
  int lockOperationKey(@Param("key") String key);

  Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(
      String operation, String idempotencyKey);
}

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      "select event from OutboxEvent event where event.publishedAt is null order by event.occurredAt")
  List<OutboxEvent> findPending(Pageable pageable);
}

interface InboxEventRepository extends JpaRepository<InboxEvent, String> {}
