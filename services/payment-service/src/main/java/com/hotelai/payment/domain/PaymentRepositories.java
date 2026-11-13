package com.hotelai.payment.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.id = :id")
    Optional<PaymentIntent> findLockedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.providerOrderId = :providerOrderId")
    Optional<PaymentIntent> findLockedByProviderOrderId(@Param("providerOrderId") String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.providerOrderId like concat(:providerOrderIdPrefix, '%')")
    List<PaymentIntent> findLockedByProviderOrderIdPrefix(
            @Param("providerOrderIdPrefix") String providerOrderIdPrefix);

    @Query(
            value = """
                    select 1
                    from (select pg_advisory_xact_lock(
                        hashtextextended(cast(:reservationId as text), 0))) as reservation_lock
                    """,
            nativeQuery = true)
    int lockReservation(@Param("reservationId") UUID reservationId);

    Optional<PaymentIntent> findByProviderRequestId(String providerRequestId);
    Optional<PaymentIntent> findByProviderOrderId(String providerOrderId);
    Optional<PaymentIntent> findByProviderTransactionId(String providerTransactionId);
    Page<PaymentIntent> findByReservationIdOrderByCreatedAtDesc(UUID reservationId, Pageable pageable);
    Page<PaymentIntent> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    boolean existsByReservationIdAndStatusIn(UUID reservationId, List<PaymentStatus> statuses);

    @Query("select coalesce(sum(p.amount), 0) from PaymentIntent p where p.reservationId = :reservationId and p.status = 'SUCCEEDED'")
    java.math.BigDecimal sumSucceeded(@Param("reservationId") UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.status in :statuses and p.expiresAt <= :now")
    List<PaymentIntent> findExpiredForUpdate(
            @Param("statuses") List<PaymentStatus> statuses, @Param("now") Instant now, Pageable pageable);

    List<PaymentIntent> findByStatusAndProviderOrderByUpdatedAtAsc(
            PaymentStatus status, PaymentProviderCode provider, Pageable pageable);
}

interface ReservationPaymentProjectionRepository extends JpaRepository<ReservationPaymentProjection, UUID> {}

interface PendingStayFolioProjectionRepository
        extends JpaRepository<PendingStayFolioProjection, UUID> {}

interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findLockedById(@Param("id") UUID id);
    List<RefundRequest> findByPaymentIdOrderByRequestedAtDesc(UUID paymentId);
    Page<RefundRequest> findByPaymentIdOrderByRequestedAtDesc(UUID paymentId, Pageable pageable);
    Page<RefundRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    List<RefundRequest> findByStatusOrderByUpdatedAtAsc(RefundStatus status, Pageable pageable);
}

interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyRecord, UUID> {
    @Query(
            value = """
                    select 1
                    from (select pg_advisory_xact_lock(
                        hashtextextended(cast(:key as text), 0))) as idempotency_lock
                    """,
            nativeQuery = true)
    int lockScopeKey(@Param("key") String key);

    Optional<PaymentIdempotencyRecord> findByKeyAndScopeAndActorId(String key, String scope, UUID actorId);
    long deleteByExpiresAtBefore(Instant now);
}

interface PaymentInboxRepository extends JpaRepository<PaymentInboxEvent, UUID> {}

interface PaymentWebhookRepository extends JpaRepository<PaymentWebhookDelivery, UUID> {
    Optional<PaymentWebhookDelivery> findByProviderAndProviderEventKey(String provider, String providerEventKey);
    List<PaymentWebhookDelivery>
            findTop50ByProviderAndSignatureValidTrueAndProcessingResultInOrderByReceivedAtAsc(
                    String provider, List<String> processingResults);
}
