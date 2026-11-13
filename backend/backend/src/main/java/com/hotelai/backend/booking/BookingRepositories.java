package com.hotelai.backend.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface BookingRepository extends JpaRepository<Booking, Long> {
  Optional<Booking> findByBookingCode(String code);

  List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<Booking> findAllByOrderByCreatedAtDesc();

  List<Booking> findByStatusAndHoldExpiresAtBefore(String status, Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id=:id")
  Optional<Booking> lockById(@Param("id") Long id);
}

interface BookingRoomRepository extends JpaRepository<BookingRoom, Long> {
  @Query(
      "select count(br) from BookingRoom br where br.roomType.id=:typeId and br.booking.status in ('PENDING_PAYMENT','CONFIRMED','CHECKED_IN') and br.booking.checkInDate<=:night and br.booking.checkOutDate>:night and (br.booking.status<>'PENDING_PAYMENT' or br.booking.holdExpiresAt>:now)")
  long occupied(
      @Param("typeId") Long typeId, @Param("night") LocalDate night, @Param("now") Instant now);

  List<BookingRoom> findByBookingId(Long bookingId);
}

interface GuestRepository extends JpaRepository<Guest, Long> {}

interface PromotionRepository extends JpaRepository<Promotion, Long> {
  Optional<Promotion> findByCodeIgnoreCase(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Promotion p where upper(p.code)=upper(:code)")
  Optional<Promotion> lockByCode(@Param("code") String code);
}

interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
  List<RefundRequest> findAllByOrderByCreatedAtDesc();
}
