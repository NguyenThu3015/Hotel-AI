package com.hotel.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.cors")
public class GatewayCorsProperties {

  private List<String> allowedOriginPatterns =
      List.of("http://localhost:3000", "http://127.0.0.1:3000");
  private List<String> allowedMethods =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  private List<String> allowedHeaders =
      List.of(
          "Authorization",
          "Content-Type",
          "Idempotency-Key",
          "X-Correlation-Id",
          "X-Signature",
          "X-Requested-With");
  private List<String> exposedHeaders =
      List.of(
          "X-Correlation-Id",
          "X-RateLimit-Remaining",
          "X-RateLimit-Replenish-Rate",
          "Retry-After");
  private boolean allowCredentials = true;
  private Duration maxAge = Duration.ofHours(1);

  public List<String> getAllowedOriginPatterns() {
    return allowedOriginPatterns;
  }

  public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  public List<String> getAllowedMethods() {
    return allowedMethods;
  }

  public void setAllowedMethods(List<String> allowedMethods) {
    this.allowedMethods = allowedMethods;
  }

  public List<String> getAllowedHeaders() {
    return allowedHeaders;
  }

  public void setAllowedHeaders(List<String> allowedHeaders) {
    this.allowedHeaders = allowedHeaders;
  }

  public List<String> getExposedHeaders() {
    return exposedHeaders;
  }

  public void setExposedHeaders(List<String> exposedHeaders) {
    this.exposedHeaders = exposedHeaders;
  }

  public boolean isAllowCredentials() {
    return allowCredentials;
  }

  public void setAllowCredentials(boolean allowCredentials) {
    this.allowCredentials = allowCredentials;
  }

  public Duration getMaxAge() {
    return maxAge;
  }

  public void setMaxAge(Duration maxAge) {
    this.maxAge = maxAge;
  }
}
