package com.hotelai.billing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillingOutboxRepository extends JpaRepository<BillingOutboxEvent, UUID> {
    @Query("select e from BillingOutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<BillingOutboxEvent> findPending(Pageable pageable);
}
