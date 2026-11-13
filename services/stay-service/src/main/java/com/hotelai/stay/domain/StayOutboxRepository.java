package com.hotelai.stay.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StayOutboxRepository extends JpaRepository<StayOutboxEvent, UUID> {
    @Query("select e from StayOutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<StayOutboxEvent> findPending(Pageable pageable);
}
