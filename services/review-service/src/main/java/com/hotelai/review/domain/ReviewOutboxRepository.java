package com.hotelai.review.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewOutboxRepository
        extends JpaRepository<ReviewOutboxEvent, UUID> {
    @Query("select e from ReviewOutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<ReviewOutboxEvent> findPending(Pageable pageable);
}
