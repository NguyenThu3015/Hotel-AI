package com.hotelai.payment.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, UUID> {
    @Query("select e from PaymentOutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<PaymentOutboxEvent> findPending(Pageable pageable);
}
