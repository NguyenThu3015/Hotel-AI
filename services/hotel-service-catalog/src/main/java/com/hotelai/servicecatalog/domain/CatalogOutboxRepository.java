package com.hotelai.servicecatalog.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEvent, UUID> {
    @Query("select e from CatalogOutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<CatalogOutboxEvent> findPending(Pageable pageable);
}
