package com.hotelai.review.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ReviewAiAnalysisClient {
    private static final Logger log = LoggerFactory.getLogger(ReviewAiAnalysisClient.class);
    private final RestClient client;

    public ReviewAiAnalysisClient(
            RestClient.Builder builder,
            @Value("${hotel.review.ai-customer-analytics-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public Optional<Analysis> analyze(UUID reviewId, String text) {
        try {
            Analysis result = client.post()
                    .uri("/api/v1/reviews/analyze")
                    .body(new AnalysisRequest(reviewId, text))
                    .retrieve()
                    .body(Analysis.class);
            return Optional.ofNullable(result);
        } catch (Exception error) {
            log.warn(
                    "Review AI analysis deferred for review {}: {}",
                    reviewId,
                    error.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    record AnalysisRequest(
            @JsonProperty("review_id") UUID reviewId,
            String text) {}

    public record Analysis(
            String sentiment,
            BigDecimal score,
            List<String> topics,
            String urgency,
            BigDecimal confidence,
            String draftResponse,
            String modelVersion,
            String provider) {}
}
