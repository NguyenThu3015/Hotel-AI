package com.hotelai.review.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class ReviewEventService {
    private final ReviewOutboxRepository outbox;
    private final CustomerIdentityProjectionRepository identities;
    private final ObjectMapper objectMapper;

    ReviewEventService(
            ReviewOutboxRepository outbox,
            CustomerIdentityProjectionRepository identities,
            ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.identities = identities;
        this.objectMapper = objectMapper;
    }

    void submitted(Review review) {
        append("review.submitted.v1", review.getId(), reviewPayload(review));
        if (review.getStatus() == ReviewStatus.PUBLISHED) {
            append("review.published.v1", review.getId(), reviewPayload(review));
        }
    }

    void moderated(Review review, ReviewModerationDecision decision) {
        Map<String, Object> payload = reviewPayload(review);
        payload.put("moderationAction", decision.getAction().name());
        payload.put("moderationReason", decision.getReason());
        payload.put("moderatedBy", decision.getDecidedBy());
        payload.put("moderatedAt", decision.getDecidedAt());
        append("review.moderated.v1", review.getId(), payload);
        if (decision.getAction() == ModerationAction.PUBLISH) {
            append("review.published.v1", review.getId(), reviewPayload(review));
        }
    }

    void responseUpserted(
            Review review, ReviewResponse response, ReviewResponseRevision revision) {
        Map<String, Object> payload = reviewPayload(review);
        payload.put("responseId", response.getId());
        payload.put("responseContent", response.getContent());
        payload.put("responseRevision", response.getRevisionNumber());
        payload.put("respondedBy", response.getRespondedBy());
        payload.put("respondedAt", response.getUpdatedAt());
        append("review.response-upserted.v1", review.getId(), payload);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("reviewId", review.getId());
        variables.put("reservationReference", review.getReservationReference());
        variables.put("responseContent", response.getContent());
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("notificationId", UUID.randomUUID());
        notification.put(
                "recipientIdentityId",
                identities.findById(review.getCustomerId())
                        .map(CustomerIdentityProjection::getIdentityId)
                        .orElse(review.getCustomerId()));
        notification.put("channel", "EMAIL");
        notification.put("recipient", review.getCustomerEmail());
        notification.put("templateCode", "REVIEW_RESPONSE");
        notification.put("locale", "vi");
        notification.put("variables", variables);
        notification.put("maxAttempts", 5);
        notification.put(
                "deduplicationKey",
                "review-response:" + review.getId() + ":" + revision.getRevisionNumber());
        append("notification.requested.v1", review.getId(), notification);
    }

    private Map<String, Object> reviewPayload(Review review) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", review.getId());
        payload.put("stayId", review.getStayId());
        payload.put("reservationId", review.getReservationId());
        payload.put("customerId", review.getCustomerId());
        payload.put("reservationReference", review.getReservationReference());
        payload.put("rating", review.getRating());
        payload.put("comment", review.getComment());
        payload.put(
                "topics",
                ReviewApplicationService.decodeTopics(review.getTopics()).stream()
                        .map(Enum::name)
                        .toList());
        payload.put("status", review.getStatus().name());
        payload.put("verified", review.isVerified());
        try {
            payload.put(
                    "moderationFlags",
                    objectMapper.readTree(review.getModerationFlags()));
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Cannot deserialize review moderation flags", error);
        }
        payload.put("createdAt", review.getCreatedAt());
        payload.put("publishedAt", review.getPublishedAt());
        return payload;
    }

    private void append(
            String eventType, UUID aggregateId, Map<String, Object> payload) {
        try {
            outbox.save(new ReviewOutboxEvent(
                    "Review",
                    aggregateId,
                    eventType,
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize review event", error);
        }
    }
}
