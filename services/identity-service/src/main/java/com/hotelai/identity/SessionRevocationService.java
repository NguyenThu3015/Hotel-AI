package com.hotelai.identity;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class SessionRevocationService {
  static final String SESSION_PREFIX = "identity:revoked-session:";
  static final String VERSION_PREFIX = "identity:revoked-session-version:";

  private final StringRedisTemplate redis;
  private final IdentityProperties properties;

  void revokeSession(UUID sessionId, String reason) {
    redis
        .opsForValue()
        .set(
            SESSION_PREFIX + sessionId,
            reason,
            properties.security().refreshTtl().plus(properties.security().accessTtl()));
  }

  void revokeVersion(UUID sessionId, long version) {
    redis
        .opsForValue()
        .set(
            VERSION_PREFIX + sessionId + ':' + version,
            "ROTATED",
            properties.security().accessTtl().plus(Duration.ofMinutes(1)));
  }
}
