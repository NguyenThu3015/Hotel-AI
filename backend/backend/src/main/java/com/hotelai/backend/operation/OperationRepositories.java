package com.hotelai.backend.operation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExtraServiceRepository extends JpaRepository<ExtraService, Long> {
  List<ExtraService> findByHotelIdAndActiveTrue(Long hotelId);
}

interface ServiceUsageRepository extends JpaRepository<ServiceUsage, Long> {
  List<ServiceUsage> findByBookingId(Long bookingId);
}

interface HousekeepingRepository extends JpaRepository<HousekeepingTask, Long> {
  List<HousekeepingTask> findByAssignedToIdOrAssignedToIsNull(Long userId);
}

interface MaintenanceRepository extends JpaRepository<MaintenanceRequest, Long> {
  List<MaintenanceRequest> findByStatusNot(String status);
}
