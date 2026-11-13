package com.hotelai.notification.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {
    Optional<NotificationJob> findBySourceEventId(String sourceEventId);
    Optional<NotificationJob> findByDeduplicationKey(String deduplicationKey);
    Page<NotificationJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<NotificationJob> findByRecipientIdentityIdOrderByCreatedAtDesc(UUID identityId, Pageable pageable);

    @Query(value = """
            SELECT * FROM notification_jobs
             WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
               AND next_attempt_at <= :now
             ORDER BY next_attempt_at, created_at
             FOR UPDATE SKIP LOCKED
             LIMIT 1
            """, nativeQuery = true)
    Optional<NotificationJob> lockNextDue(@Param("now") Instant now);
}
