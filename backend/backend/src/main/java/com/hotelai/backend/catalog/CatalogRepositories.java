package com.hotelai.backend.catalog;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface HotelRepository extends JpaRepository<Hotel, Long> {
  List<Hotel> findByActiveTrue();
}

interface AmenityRepository extends JpaRepository<Amenity, Long> {}

interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "amenities")
  List<RoomType> findByHotelIdAndActiveTrue(Long hotelId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RoomType> findWithLockById(Long id);
}

interface RoomRepository extends JpaRepository<Room, Long> {
  List<Room> findByHotelId(Long hotelId);

  long countByRoomTypeIdAndStatusNot(Long id, String status);

  List<Room> findByRoomTypeIdAndStatus(Long id, String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Room> findWithLockById(Long id);
}

interface RoomRateRepository extends JpaRepository<RoomRate, Long> {
  List<RoomRate> findByRoomTypeIdAndRateDateGreaterThanEqualAndRateDateLessThan(
      Long id, LocalDate from, LocalDate to);

  Optional<RoomRate> findByRoomTypeIdAndRateDate(Long id, LocalDate date);
}

interface RoomImageRepository extends JpaRepository<RoomImage, Long> {
  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "roomType")
  List<RoomImage> findByRoomTypeIdOrderByDisplayOrder(Long roomTypeId);
}
