package com.hotelai.backend.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiGatewayController {
  private final AiGatewayService gateway;

  @PostMapping("/chat")
  Map<?, ?> chat(
      @Valid @RequestBody ChatRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", request.message());
    if (request.sessionId() != null) body.put("session_id", request.sessionId());
    return gateway.chat(body, authorization);
  }

  @PostMapping("/recommendations")
  Map<?, ?> recommendations(
      @RequestBody Map<String, Object> request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return gateway.recommendations(request, authorization);
  }

  @PostMapping("/forecast")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Map<?, ?> forecast(
      @RequestBody Map<String, Object> request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return gateway.forecast(request, authorization);
  }

  @PostMapping("/anomalies")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Map<?, ?> anomalies(
      @RequestBody Map<String, Object> request,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return gateway.anomalies(request, authorization);
  }

  record ChatRequest(@NotBlank String message, String sessionId) {}
}
