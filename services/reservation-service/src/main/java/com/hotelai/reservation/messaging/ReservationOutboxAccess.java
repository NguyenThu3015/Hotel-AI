package com.hotelai.reservation.messaging;

import com.hotelai.reservation.domain.OutboxEvent;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationOutboxAccess {
    private final EntityManager entityManager;

    public ReservationOutboxAccess(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<OutboxEvent> pending(Pageable pageable) {
        return entityManager.createQuery(
                        "select o from OutboxEvent o where o.publishedAt is null order by o.occurredAt",
                        OutboxEvent.class)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }
}
