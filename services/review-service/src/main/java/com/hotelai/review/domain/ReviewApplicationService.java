package com.hotelai.review.domain;

import com.hotelai.review.common.ApiException;
import com.hotelai.review.domain.ReviewContentPolicy.Assessment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReviewApplicationService {
    private final StayReviewProjectionRepository stays;
    private final CustomerIdentityProjectionRepository identities;
    private final ReviewRepository reviews;
    private final ReviewResponseRepository responses;
    private final ReviewResponseRevisionRepository responseRevisions;
    private final ReviewModerationDecisionRepository decisions;
    private final ReviewContentPolicy contentPolicy;
    private final ReviewEventService events;
    private final ReviewAiAnalysisClient analysisClient;
    private final ObjectMapper objectMapper;

    public ReviewApplicationService(
            StayReviewProjectionRepository stays,
            CustomerIdentityProjectionRepository identities,
            ReviewRepository reviews,
            ReviewResponseRepository responses,
            ReviewResponseRevisionRepository responseRevisions,
            ReviewModerationDecisionRepository decisions,
            ReviewContentPolicy contentPolicy,
            ReviewEventService events,
            ReviewAiAnalysisClient analysisClient,
            ObjectMapper objectMapper) {
        this.stays = stays;
        this.identities = identities;
        this.reviews = reviews;
        this.responses = responses;
        this.responseRevisions = responseRevisions;
        this.decisions = decisions;
        this.contentPolicy = contentPolicy;
        this.events = events;
        this.analysisClient = analysisClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EligibleStay> eligibleStays(UUID identityId) {
        Set<UUID> customerIds = ownedCustomerIds(identityId);
        return stays.findByStatusAndCustomerIdInOrderByActualCheckOutDesc(
                        "CLOSED", customerIds).stream()
                .filter(stay -> !reviews.existsByStayId(stay.getStayId()))
                .map(EligibleStay::from)
                .toList();
    }

    @Transactional
    public ReviewDetails create(
            UUID identityId, String idempotencyKey, CreateCommand command) {
        requireKey(idempotencyKey);
        String requestHash = hash("CREATE", command);
        Review replay = reviews.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            try {
                replay.replay(requestHash);
            } catch (IllegalStateException error) {
                throw ApiException.conflict("idempotency-key-reused", error.getMessage());
            }
            return details(replay);
        }

        StayReviewProjection stay = stays.findById(command.stayId())
                .orElseThrow(() -> ApiException.notFound("Completed stay not found"));
        if (!stay.isClosed()) {
            throw ApiException.conflict(
                    "stay-not-completed",
                    "Only a completed stay can be reviewed");
        }
        if (!ownedCustomerIds(identityId).contains(stay.getCustomerId())) {
            throw ApiException.forbidden(
                    "You cannot review a stay owned by another customer");
        }
        if (reviews.existsByStayId(stay.getStayId())) {
            throw ApiException.conflict(
                    "stay-already-reviewed",
                    "This stay already has its verified review");
        }
        Set<ReviewTopic> topics = validateTopics(command.topics());
        Assessment assessment =
                contentPolicy.assess(stay.getCustomerId(), command.comment());
        Review review = reviews.save(new Review(
                stay,
                command.rating(),
                assessment.sanitizedComment(),
                assessment.commentHash(),
                encodeTopics(topics),
                assessment.flagsJson(),
                assessment.status(),
                idempotencyKey,
                requestHash));
        analysisClient.analyze(review.getId(), review.getComment())
                .ifPresent(analysis -> review.applyAiAnalysis(
                        analysis.sentiment(),
                        analysis.score(),
                        encodeAiTopics(analysis.topics()),
                        analysis.urgency(),
                        analysis.confidence(),
                        analysis.draftResponse(),
                        analysis.modelVersion(),
                        analysis.provider()));
        events.submitted(review);
        return details(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewDetails> mine(UUID identityId, Pageable pageable) {
        return reviews.findByCustomerIdInOrderByCreatedAtDesc(
                        ownedCustomerIds(identityId), pageable)
                .map(this::details);
    }

    @Transactional(readOnly = true)
    public Page<ReviewDetails> publicReviews(
            Integer rating, ReviewTopic topic, Pageable pageable) {
        Specification<Review> specification =
                (root, query, builder) ->
                        builder.equal(root.get("status"), ReviewStatus.PUBLISHED);
        if (rating != null) {
            if (rating < 1 || rating > 5) {
                throw ApiException.validation("Rating filter must be between 1 and 5");
            }
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("rating"), rating));
        }
        if (topic != null) {
            specification = specification.and((root, query, builder) ->
                    builder.like(root.get("topics"), "%" + topic.name() + "%"));
        }
        return reviews.findAll(specification, pageable).map(this::details);
    }

    @Transactional(readOnly = true)
    public ReviewDetails publicDetail(UUID reviewId) {
        Review review = requiredReview(reviewId);
        if (review.getStatus() != ReviewStatus.PUBLISHED) {
            throw ApiException.notFound("Published review not found");
        }
        return details(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewDetails> adminReviews(
            ReviewStatus status,
            Integer rating,
            String search,
            Pageable pageable) {
        Specification<Review> specification =
                (root, query, builder) -> builder.conjunction();
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        if (rating != null) {
            if (rating < 1 || rating > 5) {
                throw ApiException.validation("Rating filter must be between 1 and 5");
            }
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("rating"), rating));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("reservationReference")), pattern),
                    builder.like(builder.lower(root.get("authorDisplayName")), pattern),
                    builder.like(builder.lower(root.get("comment")), pattern)));
        }
        return reviews.findAll(specification, pageable).map(this::details);
    }

    @Transactional
    public ReviewDetails moderate(
            UUID actorId,
            UUID reviewId,
            String idempotencyKey,
            ModerationCommand command) {
        requireKey(idempotencyKey);
        String requestHash = hash("MODERATE:" + reviewId, command);
        ReviewModerationDecision replay =
                decisions.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            if (!replay.getReviewId().equals(reviewId)
                    || !replay.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict(
                        "idempotency-key-reused",
                        "Idempotency-Key was already used for another moderation decision");
            }
            return details(requiredReview(reviewId));
        }
        String reason = command.reason();
        if (command.action() == ModerationAction.PUBLISH
                && (reason == null || reason.isBlank())) {
            reason = "Approved by moderator";
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.validation(
                    "A reason is required when hiding or rejecting a review");
        }
        Review review = requiredReview(reviewId);
        try {
            review.moderate(command.action(), Instant.now());
        } catch (IllegalStateException error) {
            throw ApiException.conflict("review-status-unchanged", error.getMessage());
        }
        ReviewModerationDecision decision = decisions.save(
                new ReviewModerationDecision(
                        reviewId,
                        command.action(),
                        reason,
                        actorId,
                        idempotencyKey,
                        requestHash,
                        Instant.now()));
        events.moderated(review, decision);
        return details(review);
    }

    @Transactional
    public ReviewDetails upsertResponse(
            UUID actorId,
            UUID reviewId,
            String idempotencyKey,
            String content) {
        requireKey(idempotencyKey);
        if (content == null || content.isBlank() || content.strip().length() > 5000) {
            throw ApiException.validation(
                    "Official response must contain 1-5000 characters");
        }
        String requestHash = hash(
                "RESPONSE:" + reviewId, Map.of("content", content.strip()));
        ReviewResponseRevision replay =
                responseRevisions.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            if (!replay.getReviewId().equals(reviewId)
                    || !replay.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict(
                        "idempotency-key-reused",
                        "Idempotency-Key was already used for another response revision");
            }
            return details(requiredReview(reviewId));
        }
        Review review = requiredReview(reviewId);
        if (review.getStatus() != ReviewStatus.PUBLISHED) {
            throw ApiException.conflict(
                    "review-not-published",
                    "The hotel can only respond to a published review");
        }
        ReviewResponse response = responses.findByReviewId(reviewId).orElse(null);
        if (response == null) {
            response = new ReviewResponse(reviewId, content, actorId, Instant.now());
        } else {
            response.revise(content, actorId, Instant.now());
        }
        response = responses.save(response);
        ReviewResponseRevision revision = responseRevisions.save(
                new ReviewResponseRevision(
                        response, idempotencyKey, requestHash, actorId));
        events.responseUpserted(review, response, revision);
        return details(review);
    }

    @Transactional(readOnly = true)
    public ReviewSummary summary() {
        long total = reviews.countByStatus(ReviewStatus.PUBLISHED);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        long weighted = 0;
        for (int rating = 1; rating <= 5; rating++) {
            long count = reviews.countByStatusAndRating(ReviewStatus.PUBLISHED, rating);
            distribution.put(rating, count);
            weighted += count * rating;
        }
        double average = total == 0 ? 0 : (double) weighted / total;
        return new ReviewSummary(total, Math.round(average * 100.0) / 100.0, distribution);
    }

    private ReviewDetails details(Review review) {
        return new ReviewDetails(
                review,
                responses.findByReviewId(review.getId()).orElse(null),
                responseRevisions.findByReviewIdOrderByRevisionNumberAsc(review.getId()),
                decisions.findByReviewIdOrderByDecidedAtAsc(review.getId()));
    }

    private Set<UUID> ownedCustomerIds(UUID identityId) {
        Set<UUID> values = new LinkedHashSet<>();
        values.add(identityId);
        identities.findByIdentityId(identityId)
                .map(CustomerIdentityProjection::getCustomerId)
                .ifPresent(values::add);
        return values;
    }

    private Review requiredReview(UUID id) {
        return reviews.findById(id)
                .orElseThrow(() -> ApiException.notFound("Review not found"));
    }

    private static Set<ReviewTopic> validateTopics(Collection<ReviewTopic> values) {
        if (values == null || values.isEmpty()) {
            throw ApiException.validation("At least one review topic is required");
        }
        return EnumSet.copyOf(values);
    }

    private static String encodeTopics(Set<ReviewTopic> topics) {
        return topics.stream().map(Enum::name).sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    public static List<ReviewTopic> decodeTopics(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<ReviewTopic> topics = new ArrayList<>();
        for (String token : value.split(",")) {
            topics.add(ReviewTopic.valueOf(token));
        }
        return List.copyOf(topics);
    }

    private String encodeAiTopics(List<String> topics) {
        try {
            return objectMapper.writeValueAsString(topics == null ? List.of() : topics);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot encode AI review topics", error);
        }
    }

    private static void requireKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,160}")) {
            throw ApiException.validation(
                    "Idempotency-Key must contain 8-160 safe characters");
        }
    }

    private String hash(String operation, Object value) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("operation", operation);
            canonical.put("request", value);
            byte[] bytes = objectMapper.writeValueAsString(canonical)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash review command", error);
        }
    }

    public record CreateCommand(
            UUID stayId, int rating, Set<ReviewTopic> topics, String comment) {}

    public record ModerationCommand(
            ModerationAction action, String reason) {}

    public record EligibleStay(
            UUID stayId,
            UUID reservationId,
            String reservationReference,
            java.time.LocalDate checkIn,
            java.time.LocalDate checkOut,
            Instant actualCheckOut,
            List<String> roomNumbers) {
        static EligibleStay from(StayReviewProjection value) {
            return new EligibleStay(
                    value.getStayId(),
                    value.getReservationId(),
                    value.getReservationReference(),
                    value.getScheduledCheckIn(),
                    value.getScheduledCheckOut(),
                    value.getActualCheckOut(),
                    value.roomNumberList());
        }
    }

    public record ReviewDetails(
            Review review,
            ReviewResponse response,
            List<ReviewResponseRevision> responseHistory,
            List<ReviewModerationDecision> moderationHistory) {}

    public record ReviewSummary(
            long publishedCount,
            double averageRating,
            Map<Integer, Long> ratingDistribution) {}
}
