package com.hotelai.servicecatalog.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HotelServiceItemRepository extends JpaRepository<HotelServiceItem, UUID> {
    List<HotelServiceItem> findAllByOrderByCodeAsc();
    List<HotelServiceItem> findByActiveTrueOrderByCodeAsc();
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HotelServiceItem s where s.id = :id")
    Optional<HotelServiceItem> findLockedById(@Param("id") UUID id);
}

interface ServicePriceVersionRepository extends JpaRepository<ServicePriceVersion, UUID> {
    Optional<ServicePriceVersion> findFirstByServiceIdAndValidToIsNullOrderByValidFromDesc(
            UUID serviceId);

    List<ServicePriceVersion> findByServiceIdAndValidFromLessThanEqualOrderByValidFromDesc(
            UUID serviceId, Instant at);

    List<ServicePriceVersion> findByServiceIdOrderByValidFromDesc(UUID serviceId);
}

interface StayProjectionRepository extends JpaRepository<StayProjection, UUID> {
    List<StayProjection> findTop100ByOrderBySourceUpdatedAtDesc();
}

interface ServiceUsageRepository extends JpaRepository<ServiceUsage, UUID> {
    List<ServiceUsage> findByStayIdOrderByOccurredAtDesc(UUID stayId);
    boolean existsByServiceId(UUID serviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from ServiceUsage u where u.id = :id")
    Optional<ServiceUsage> findLockedById(@Param("id") UUID id);
}

interface PreStayServiceOrderRepository extends JpaRepository<PreStayServiceOrder, UUID> {
    List<PreStayServiceOrder> findByReservationIdOrderByScheduledAtAsc(UUID reservationId);
    List<PreStayServiceOrder> findByCustomerIdOrderByScheduledAtDesc(UUID customerId);
    List<PreStayServiceOrder> findTop100ByOrderByCreatedAtDesc();
}

interface ServiceUsageRevisionRepository extends JpaRepository<ServiceUsageRevision, UUID> {
    Optional<ServiceUsageRevision> findByIdempotencyKey(String idempotencyKey);
    List<ServiceUsageRevision> findByUsageIdOrderByRevisionNumberAsc(UUID usageId);
}

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
