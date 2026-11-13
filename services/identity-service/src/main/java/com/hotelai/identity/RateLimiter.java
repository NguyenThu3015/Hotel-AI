package com.hotelai.identity;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class RateLimiter {
  private final RateLimitRepository limits;

  void check(String action, String subject, int maxAttempts, long windowSeconds) {
    String subjectHash = Hashing.sha256(subject);
    RateLimit limit = limits.findBySubjectHashAndAction(subjectHash, action).orElse(null);
    if (limit == null) {
      limits.save(new RateLimit(subjectHash, action));
      return;
    }
    if (!limit.incrementOrReset(Instant.now(), windowSeconds, maxAttempts)) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "rate_limit_exceeded",
          "Bạn đã thử quá nhiều lần. Vui lòng đợi rồi thử lại.");
    }
    limits.save(limit);
  }
}
