package com.hotelai.backend.review;

import com.hotelai.backend.account.User;
import com.hotelai.backend.account.UserRepository;
import com.hotelai.backend.ai.AiGatewayService;
import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.booking.Booking;
import com.hotelai.backend.booking.BookingService;
import com.hotelai.backend.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewRepository reviews;
  private final BookingService bookings;
  private final UserRepository users;
  private final AuditService audit;
  private final AiGatewayService ai;

  @GetMapping("/catalog/hotels/{id}/reviews")
  List<View> list(@PathVariable Long id) {
    return reviews.findByBookingHotelIdAndStatusOrderByCreatedAtDesc(id, "PUBLISHED").stream()
        .map(View::of)
        .toList();
  }

  @PostMapping("/reviews")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  View create(Principal p, @Valid @RequestBody Create r) {
    Booking b = bookings.lock(r.bookingId);
    User u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    if (!b.getUser().getId().equals(u.getId()))
      throw new ApiException(HttpStatus.FORBIDDEN, "Không thể đánh giá booking của người khác");
    if (!"CHECKED_OUT".equals(b.getStatus()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ booking đã check-out mới được đánh giá");
    if (reviews.existsByBookingId(b.getId()))
      throw new ApiException(HttpStatus.CONFLICT, "Booking đã được đánh giá");
    Review review = reviews.save(new Review(b, u, r.rating, r.comment));
    if (r.comment != null && !r.comment.isBlank()) {
      try {
        Map<?, ?> result = ai.analyzeReview(java.util.Map.of("text", r.comment));
        Number score = (Number) result.get("score");
        List<String> topics =
            result.get("topics") instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
        review.analyze(
            String.valueOf(result.get("sentiment")),
            BigDecimal.valueOf(score == null ? 0 : score.doubleValue()),
            topics);
      } catch (RuntimeException ignored) {
        // Đánh giá vẫn được ghi nhận khi dịch vụ AI tạm thời không sẵn sàng.
      }
    }
    audit.record("CREATE_REVIEW", "REVIEW", review.getId(), String.valueOf(r.rating));
    return View.of(review);
  }

  @PostMapping("/admin/reviews/{id}/response")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional
  View respond(@PathVariable Long id, @Valid @RequestBody Response r) {
    Review v =
        reviews
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"));
    v.respond(r.content);
    return View.of(v);
  }

  @PatchMapping("/admin/reviews/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional
  View moderate(@PathVariable Long id, @Valid @RequestBody Status r) {
    Review v = reviews.findById(id).orElseThrow();
    v.moderate(r.status);
    return View.of(v);
  }

  @GetMapping("/admin/reviews")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional(readOnly = true)
  List<View> adminReviews() {
    return reviews.findAll().stream().map(View::of).toList();
  }

  record Create(
      @NotNull Long bookingId, @Min(1) @Max(5) int rating, @Size(max = 5000) String comment) {}

  record Response(@NotBlank @Size(max = 5000) String content) {}

  record Status(@Pattern(regexp = "PUBLISHED|HIDDEN") String status) {}

  record View(
      Long id,
      String bookingCode,
      int rating,
      String comment,
      String status,
      String sentiment,
      BigDecimal sentimentScore,
      List<String> sentimentTopics,
      String hotelResponse,
      Instant createdAt) {
    static View of(Review r) {
      return new View(
          r.getId(),
          r.getBooking().getBookingCode(),
          r.getRating(),
          r.getComment(),
          r.getStatus(),
          r.getSentiment(),
          r.getSentimentScore(),
          r.getSentimentTopics(),
          r.getHotelResponse(),
          r.getCreatedAt());
    }
  }
}
