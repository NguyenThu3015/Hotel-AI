package com.hotelai.backend.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
  boolean existsByBookingId(Long id);

  List<Review> findByBookingHotelIdAndStatusOrderByCreatedAtDesc(Long hotelId, String status);
}
