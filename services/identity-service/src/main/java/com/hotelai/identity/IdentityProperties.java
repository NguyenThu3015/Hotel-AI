package com.hotelai.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(String hotelId, Security security, Outbox outbox) {

  public IdentityProperties {
    if (hotelId == null || hotelId.isBlank()) hotelId = "HOTEL-1";
    if (security == null) security = new Security(null, null, null, null, null, 15, 7, 10, 15);
    if (outbox == null) outbox = new Outbox(null, 50, 1000);
  }

  public record Security(
      String issuer,
      String audience,
      String keyId,
      String privateKeyLocation,
      String publicKeyLocation,
      long accessTokenMinutes,
      long refreshTokenDays,
      long otpMinutes,
      long passwordResetMinutes) {
    public Security {
      if (issuer == null || issuer.isBlank()) issuer = "http://identity-service:8101";
      if (audience == null || audience.isBlank()) audience = "hotel-api";
      if (keyId == null || keyId.isBlank()) keyId = "identity-dev-1";
      if (accessTokenMinutes <= 0) accessTokenMinutes = 15;
      if (refreshTokenDays <= 0) refreshTokenDays = 7;
      if (otpMinutes <= 0) otpMinutes = 10;
      if (passwordResetMinutes <= 0) passwordResetMinutes = 15;
    }

    Duration accessTtl() {
      return Duration.ofMinutes(accessTokenMinutes);
    }

    Duration refreshTtl() {
      return Duration.ofDays(refreshTokenDays);
    }

    Duration otpTtl() {
      return Duration.ofMinutes(otpMinutes);
    }

    Duration resetTtl() {
      return Duration.ofMinutes(passwordResetMinutes);
    }
  }

  public record Outbox(String exchange, int batchSize, long fixedDelayMs) {
    public Outbox {
      if (exchange == null || exchange.isBlank()) exchange = "hotel.events";
      if (batchSize <= 0) batchSize = 50;
      if (fixedDelayMs <= 0) fixedDelayMs = 1000;
    }
  }
}
