package com.hotelai.reservation.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {
    @EntityGraph(attributePaths = "guests")
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findDetailedById(@Param("id") UUID id);

    Page<Reservation> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Optional<Reservation> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.holdExpiresAt <= :now and r.status in :statuses order by r.holdExpiresAt")
    List<Reservation> findExpiredHoldsForUpdate(
            @Param("now") Instant now,
            @Param("statuses") List<ReservationStatus> statuses,
            Pageable pageable);
}
