package com.hotelai.inventory.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FloorRepository extends JpaRepository<Floor, UUID> {
    List<Floor> findAllByOrderByLevelNumberAsc();
    long countByIdNotAndLevelNumber(UUID id, int levelNumber);
}

interface DestinationRepository extends JpaRepository<Destination, String> {
    List<Destination> findByActiveTrueOrderByDisplayOrderAsc();
    List<Destination> findAllByOrderByDisplayOrderAscNameAsc();
}

interface AmenityRepository extends JpaRepository<Amenity, UUID> {
    List<Amenity> findAllByOrderByNameAsc();
    List<Amenity> findByActiveTrueOrderByNameAsc();
}

interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    @EntityGraph(attributePaths = {"amenities", "images"})
    @Query("select distinct t from RoomType t where t.published=true and t.active=true order by t.name")
    List<RoomType> findPublishedDetailed();

    @EntityGraph(attributePaths = {"amenities", "images"})
    @Query("select distinct t from RoomType t order by t.name")
    List<RoomType> findAllDetailed();

    @EntityGraph(attributePaths = {"amenities", "images"})
    @Query("select distinct t from RoomType t where t.id=:id")
    Optional<RoomType> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RoomType t where t.id=:id")
    Optional<RoomType> findByIdForUpdate(@Param("id") UUID id);
}

interface RoomImageRepository extends JpaRepository<RoomImage, UUID> {
    @Query("""
            select i from RoomImage i
            join fetch i.roomType t
            where t.id=:roomTypeId
            order by i.displayOrder, i.id
            """)
    List<RoomImage> findByRoomTypeIdOrderByDisplayOrderAscIdAsc(
            @Param("roomTypeId") UUID roomTypeId);
}

interface RoomRepository extends JpaRepository<Room, UUID> {
    @EntityGraph(attributePaths = {"floor", "roomType"})
    @Query("select r from Room r order by r.floor.levelNumber, r.roomNumber")
    List<Room> findAllDetailed();

    @EntityGraph(attributePaths = {"floor", "roomType"})
    @Query("select r from Room r where r.id=:id")
    Optional<Room> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id=:id")
    Optional<Room> findForStatusChange(@Param("id") UUID id);

    @Query("select count(r) from Room r where r.roomType.id=:roomTypeId and r.active=true")
    long countActiveByRoomType(@Param("roomTypeId") UUID roomTypeId);

    long countByFloorIdAndActiveTrue(UUID floorId);
    long countByRoomTypeIdAndActiveTrue(UUID roomTypeId);
    List<Room> findByOperationalStatusOrderByRoomNumberAsc(RoomStatus status);
}

interface RoomStatusHistoryRepository extends JpaRepository<RoomStatusHistory, UUID> {
    List<RoomStatusHistory> findTop100ByRoomIdOrderByChangedAtDesc(UUID roomId);
}

interface MaintenanceBlockRepository extends JpaRepository<MaintenanceBlock, UUID> {
    Optional<MaintenanceBlock> findFirstByRoomIdAndStatusOrderByCreatedAtDesc(UUID roomId, String status);
    List<MaintenanceBlock> findAllByOrderByCreatedAtDesc();
    List<MaintenanceBlock> findByStatusOrderByCreatedAtDesc(String status);

    @Query("""
            select count(distinct m.room.id) from MaintenanceBlock m
            where m.room.roomType.id=:roomTypeId and m.room.active=true and m.status='OPEN'
              and m.startDate < :checkOut and (m.endDate is null or m.endDate > :checkIn)
            """)
    long countUnavailableRooms(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);
}

interface HotelProfileRepository extends JpaRepository<HotelProfile, Short> {}

interface HousekeepingTaskRepository extends JpaRepository<HousekeepingTask, UUID> {
    boolean existsByRoomIdAndStatusIn(UUID roomId, List<String> statuses);

    @EntityGraph(attributePaths = "room")
    List<HousekeepingTask> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from HousekeepingTask t join fetch t.room where t.id=:id")
    Optional<HousekeepingTask> findByIdForUpdate(@Param("id") UUID id);
}

interface InventoryHoldRepository extends JpaRepository<InventoryHold, UUID> {
    @Query(value = """
            select 1
            from (select pg_advisory_xact_lock(
                    hashtextextended(cast(:key as text), 0))) as idempotency_lock
            """, nativeQuery = true)
    int lockIdempotencyKey(@Param("key") String key);

    Optional<InventoryHold> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from InventoryHold h where h.id=:id")
    Optional<InventoryHold> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from InventoryHold h
            where h.reservationId=:reservationId and h.status='CONFIRMED'
            """)
    Optional<InventoryHold> findConfirmedByReservationIdForUpdate(
            @Param("reservationId") UUID reservationId);

    @Query("""
            select coalesce(sum(h.quantity), 0) from InventoryHold h
            where h.roomType.id=:roomTypeId
              and h.checkIn < :checkOut and h.checkOut > :checkIn
              and (h.status='CONFIRMED' or (h.status='HELD' and h.expiresAt > :now))
            """)
    long sumCommittedQuantity(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("now") Instant now);

    @Query("""
            select (count(h) > 0) from InventoryHold h
            where h.roomType.id=:roomTypeId and h.checkOut > :today
              and (h.status='CONFIRMED' or (h.status='HELD' and h.expiresAt > :now))
            """)
    boolean hasFutureCommitments(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("today") LocalDate today,
            @Param("now") Instant now);

    @Query("select h from InventoryHold h where h.status='HELD' and h.expiresAt <= :now order by h.expiresAt")
    List<InventoryHold> findExpired(@Param("now") Instant now, Pageable pageable);
}

interface RoomTypePriceSnapshotRepository extends JpaRepository<RoomTypePriceSnapshot, UUID> {}
interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<OutboxEvent> findPending(Pageable pageable);
}
