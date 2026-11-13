package com.hotelai.stay.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReservationStayProjectionRepository
        extends JpaRepository<ReservationStayProjection, UUID> {
    @Query("""
            select r from ReservationStayProjection r
            where r.status in :statuses
              and (
                lower(r.reference) like lower(concat('%', :query, '%'))
                or lower(r.contactName) like lower(concat('%', :query, '%'))
                or r.contactPhone like concat('%', :query, '%')
              )
            order by r.checkIn desc
            """)
    List<ReservationStayProjection> search(
            @Param("statuses") List<String> statuses,
            @Param("query") String query,
            Pageable pageable);
}

interface RoomTypeProjectionRepository extends JpaRepository<RoomTypeProjection, UUID> {}

interface RoomProjectionRepository extends JpaRepository<RoomProjection, UUID> {}

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}

interface StayRepository extends JpaRepository<Stay, UUID> {
    Optional<Stay> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stay s where s.id=:id")
    Optional<Stay> findLockedById(@Param("id") UUID id);

    List<Stay> findByStatusOrderByUpdatedAtAsc(StayStatus status, Pageable pageable);
}

interface StayGuestRepository extends JpaRepository<StayGuest, UUID> {
    List<StayGuest> findByStayIdOrderByPrimaryGuestDescCreatedAtAsc(UUID stayId);
}

interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, UUID> {
    List<RoomAssignment> findByStayIdOrderByCreatedAtAsc(UUID stayId);
    List<RoomAssignment> findByStayIdAndStatusOrderByCreatedAtAsc(
            UUID stayId, AssignmentStatus status);
    Optional<RoomAssignment> findByStayIdAndRoomIdAndStatus(
            UUID stayId, UUID roomId, AssignmentStatus status);
}

interface StayFolioEntryRepository extends JpaRepository<StayFolioEntry, UUID> {
    List<StayFolioEntry> findByStayIdOrderByRecordedAtAsc(UUID stayId);
    boolean existsByOperationId(UUID operationId);
}

interface StayOperationRepository extends JpaRepository<StayOperation, UUID> {
    Optional<StayOperation> findByStayIdAndTypeAndIdempotencyKey(
            UUID stayId, OperationType type, String idempotencyKey);
    Optional<StayOperation> findFirstByStayIdAndTypeOrderByCreatedAtDesc(
            UUID stayId, OperationType type);
    List<StayOperation> findByStayIdOrderByCreatedAtAsc(UUID stayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StayOperation o where o.id=:id")
    Optional<StayOperation> findLockedById(@Param("id") UUID id);

    @Query("select o from StayOperation o where o.status <> 'COMPLETED' order by o.updatedAt")
    List<StayOperation> findPending(Pageable pageable);

    @Query("""
            select o from StayOperation o
            where o.status = 'COMPLETED'
              and o.type in ('ROOM_CHANGE', 'EXTENSION')
              and o.amountDelta <> 0
              and not exists (
                  select f.id from StayFolioEntry f where f.operationId = o.id
              )
            order by o.updatedAt
            """)
    List<StayOperation> findCompletedWithMissingFolio(Pageable pageable);
}
