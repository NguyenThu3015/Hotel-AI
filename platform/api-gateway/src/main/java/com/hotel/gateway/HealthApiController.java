package com.hotel.gateway;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthApiController {
  @GetMapping("/api/v1/ping")
  Map<String, Object> ping() {
    return Map.of(
        "status", "UP",
        "service", "api-gateway",
        "architecture", "microservices",
        "timestamp", Instant.now());
  }
}
