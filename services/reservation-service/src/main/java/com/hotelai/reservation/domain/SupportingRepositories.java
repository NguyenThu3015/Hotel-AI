package com.hotelai.reservation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReservationChangeRequestRepository extends JpaRepository<ReservationChangeRequest, UUID> {
    Optional<ReservationChangeRequest> findByReservationIdAndStatus(UUID reservationId, ChangeRequestStatus status);
    List<ReservationChangeRequest> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);
}

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    @Query(value = """
            select 1
            from (select pg_advisory_xact_lock(
                    hashtextextended(cast(:key as text), 0))) as idempotency_lock
            """, nativeQuery = true)
    int lockScopeKey(@Param("key") String key);

    Optional<IdempotencyRecord> findByKeyAndScopeAndActorId(String key, String scope, UUID actorId);
}

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("select o from OutboxEvent o where o.publishedAt is null order by o.occurredAt")
    List<OutboxEvent> findPending(Pageable pageable);
}

interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {}
