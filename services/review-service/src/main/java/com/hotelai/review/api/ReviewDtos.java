package com.hotelai.review.api;

import com.hotelai.review.domain.ModerationAction;
import com.hotelai.review.domain.Review;
import com.hotelai.review.domain.ReviewApplicationService;
import com.hotelai.review.domain.ReviewApplicationService.EligibleStay;
import com.hotelai.review.domain.ReviewApplicationService.ReviewDetails;
import com.hotelai.review.domain.ReviewApplicationService.ReviewSummary;
import com.hotelai.review.domain.ReviewModerationDecision;
import com.hotelai.review.domain.ReviewResponse;
import com.hotelai.review.domain.ReviewResponseRevision;
import com.hotelai.review.domain.ReviewStatus;
import com.hotelai.review.domain.ReviewTopic;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class ReviewDtos {
    private ReviewDtos() {}

    enum ReviewViewScope {
        PUBLIC,
        OWNER,
        ADMIN
    }

    public record CreateReviewRequest(
            @NotNull UUID stayId,
            @Min(1) @Max(5) int rating,
            @NotEmpty Set<ReviewTopic> topics,
            @NotBlank @Size(max = 5000) String comment) {}

    public record ModerationRequest(
            @NotNull ModerationAction action,
            @Size(max = 500) String reason) {}

    public record OfficialResponseRequest(
            @NotBlank @Size(max = 5000) String content) {}

    public record EligibleStayView(
            UUID stayId,
            UUID reservationId,
            String reservationReference,
            LocalDate checkIn,
            LocalDate checkOut,
            Instant actualCheckOut,
            List<String> roomNumbers) {
        static EligibleStayView from(EligibleStay value) {
            return new EligibleStayView(
                    value.stayId(),
                    value.reservationId(),
                    value.reservationReference(),
                    value.checkIn(),
                    value.checkOut(),
                    value.actualCheckOut(),
                    value.roomNumbers());
        }
    }

    public record HotelResponseView(
            UUID id,
            String content,
            UUID respondedBy,
            Instant respondedAt,
            Instant updatedAt,
            int revision) {
        static HotelResponseView from(ReviewResponse value, boolean includeActor) {
            return value == null ? null : new HotelResponseView(
                    value.getId(),
                    value.getContent(),
                    includeActor ? value.getRespondedBy() : null,
                    value.getRespondedAt(),
                    value.getUpdatedAt(),
                    value.getRevisionNumber());
        }
    }

    public record ResponseRevisionView(
            int revision,
            String content,
            UUID changedBy,
            Instant changedAt) {
        static ResponseRevisionView from(ReviewResponseRevision value) {
            return new ResponseRevisionView(
                    value.getRevisionNumber(),
                    value.getContent(),
                    value.getChangedBy(),
                    value.getChangedAt());
        }
    }

    public record ModerationDecisionView(
            ModerationAction action,
            String reason,
            UUID decidedBy,
            Instant decidedAt) {
        static ModerationDecisionView from(ReviewModerationDecision value) {
            return new ModerationDecisionView(
                    value.getAction(),
                    value.getReason(),
                    value.getDecidedBy(),
                    value.getDecidedAt());
        }
    }

    public record AiAnalysisView(
            String sentiment,
            BigDecimal score,
            List<String> topics,
            String urgency,
            BigDecimal confidence,
            String draftResponse,
            String modelVersion,
            String provider,
            Instant analyzedAt) {
        static AiAnalysisView from(Review value, ObjectMapper objectMapper) {
            if (value.getAiAnalyzedAt() == null) return null;
            return new AiAnalysisView(
                    value.getAiSentiment(),
                    value.getAiSentimentScore(),
                    stringList(value.getAiTopics(), objectMapper),
                    value.getAiUrgency(),
                    value.getAiConfidence(),
                    value.getAiDraftResponse(),
                    value.getAiModelVersion(),
                    value.getAiProvider(),
                    value.getAiAnalyzedAt());
        }
    }

    public record ReviewView(
            UUID id,
            UUID stayId,
            UUID reservationId,
            String reservationReference,
            String authorDisplayName,
            int rating,
            String comment,
            List<ReviewTopic> topics,
            ReviewStatus status,
            boolean verified,
            List<String> moderationFlags,
            Instant createdAt,
            Instant publishedAt,
            HotelResponseView hotelResponse,
            AiAnalysisView aiAnalysis,
            List<ResponseRevisionView> responseHistory,
            List<ModerationDecisionView> moderationHistory) {
        static ReviewView from(
                ReviewDetails details, ReviewViewScope scope,
                ObjectMapper objectMapper) {
            Review value = details.review();
            boolean includeOwnerData = scope != ReviewViewScope.PUBLIC;
            boolean includeAdministrativeData = scope == ReviewViewScope.ADMIN;
            return new ReviewView(
                    value.getId(),
                    includeOwnerData ? value.getStayId() : null,
                    includeOwnerData ? value.getReservationId() : null,
                    includeOwnerData ? value.getReservationReference() : null,
                    value.getAuthorDisplayName(),
                    value.getRating(),
                    value.getComment(),
                    ReviewApplicationService.decodeTopics(value.getTopics()),
                    value.getStatus(),
                    value.isVerified(),
                    includeAdministrativeData
                            ? flags(value.getModerationFlags(), objectMapper)
                            : List.of(),
                    value.getCreatedAt(),
                    value.getPublishedAt(),
                    HotelResponseView.from(
                            details.response(), includeAdministrativeData),
                    includeAdministrativeData
                            ? AiAnalysisView.from(value, objectMapper)
                            : null,
                    includeAdministrativeData
                            ? details.responseHistory().stream()
                                .map(ResponseRevisionView::from).toList()
                            : List.of(),
                    includeAdministrativeData
                            ? details.moderationHistory().stream()
                                .map(ModerationDecisionView::from).toList()
                            : List.of());
        }
    }

    public record ReviewSummaryView(
            long publishedCount,
            double averageRating,
            Map<Integer, Long> ratingDistribution) {
        static ReviewSummaryView from(ReviewSummary value) {
            return new ReviewSummaryView(
                    value.publishedCount(),
                    value.averageRating(),
                    value.ratingDistribution());
        }
    }

    private static List<String> flags(String value, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("Cannot deserialize moderation flags", error);
        }
    }

    private static List<String> stringList(String value, ObjectMapper objectMapper) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("Cannot deserialize AI review topics", error);
        }
    }
}
