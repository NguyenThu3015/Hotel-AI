package com.hotelai.inventory.messaging;

import com.hotelai.inventory.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface InventoryOutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<OutboxEvent> findPending(Pageable pageable);
}
