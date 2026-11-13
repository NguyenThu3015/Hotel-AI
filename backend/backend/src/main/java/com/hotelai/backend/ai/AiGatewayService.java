package com.hotelai.backend.ai;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiGatewayService {
  private final RestClient client;

  public AiGatewayService(@Value("${hotel.ai.base-url}") String baseUrl) {
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
  }

  public Map<?, ?> chat(Map<String, Object> request, String authorization) {
    return post("/api/v1/chat", request, authorization);
  }

  public Map<?, ?> recommendations(Map<String, Object> request, String authorization) {
    return post("/api/v1/recommendations", request, authorization);
  }

  public Map<?, ?> analyzeReview(Map<String, Object> request) {
    return post("/api/v1/reviews/analyze", request, null);
  }

  public Map<?, ?> forecast(Map<String, Object> request, String authorization) {
    return post("/api/v1/forecast", request, authorization);
  }

  public Map<?, ?> anomalies(Map<String, Object> request, String authorization) {
    return post("/api/v1/anomalies", request, authorization);
  }

  private Map<?, ?> post(String path, Map<String, Object> body, String authorization) {
    RestClient.RequestBodySpec request =
        client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
    if (authorization != null && !authorization.isBlank()) {
      request.header("Authorization", authorization);
    }
    return request.body(body).retrieve().body(Map.class);
  }
}
